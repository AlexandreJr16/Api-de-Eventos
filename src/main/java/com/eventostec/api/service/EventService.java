package com.eventostec.api.service;

import com.amazonaws.services.s3.AmazonS3;
import com.eventostec.api.domain.address.Address;
import com.eventostec.api.domain.coupon.Coupon;
import com.eventostec.api.domain.event.Event;
import com.eventostec.api.domain.event.EventDetailsDTO;
import com.eventostec.api.domain.event.EventRequestDTO;
import com.eventostec.api.domain.event.EventResponseDTO;
import com.eventostec.api.repositories.AddressRepository;
import com.eventostec.api.repositories.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EventService {

    @Value("${aws.bucket.name}")
    private String bucketName;

    @Autowired
    private EventRepository repository;
    @Autowired
    private AmazonS3 s3Client;
    @Autowired
    private AddressService addressService;
    @Autowired
    private CouponService couponService;

    public Event createEvent(EventRequestDTO data){
        String imgUrl = null;
        if(data.image() != null){
           imgUrl=  this.uploadImage(data.image());
        }

        Event newEvent = new Event();

        newEvent.setTitle(data.title());
        newEvent.setDescription(data.description());
        newEvent.setEventUrl(data.eventUrl());
        newEvent.setDate(new Date(data.date()));
        newEvent.setImgUrl(imgUrl);
        newEvent.setRemote(data.remote());
        System.out.println(newEvent);

        repository.save(newEvent);

        if(!data.remote()) this.addressService.createAddress(data, newEvent);


        return newEvent;
    }

    public EventDetailsDTO getEventDetails(UUID eventId){
        Event event = repository.findById(eventId).orElseThrow(()->new IllegalArgumentException("Event not found"));
        List<Coupon> coupons = couponService.consultCoupon(eventId, new Date());
        Optional<Address> address = addressService.findByEventId(eventId);

        List<EventDetailsDTO.CouponDTO> couponsDTOs = coupons.stream().map(coupon-> new EventDetailsDTO.CouponDTO(
                coupon.getCode(),
                coupon.getDiscount(),
                coupon.getValid()
        )).collect(Collectors.toList());
        return new EventDetailsDTO(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getDate(),
                address.isPresent() ? address.get().getCity():"",
                address.isPresent() ? address.get().getUf() : "",
                event.getImgUrl(),
                event.getEventUrl(),
                couponsDTOs

        );
    }
    public List<EventResponseDTO> getUpcommingEvents(int page, int size){
        Pageable pageable = PageRequest.of(page, size);
        Page<Event> eventsPage = this.repository.findUpcommingEvents(new Date(), pageable);

        return eventsPage.map(event -> new EventResponseDTO(
                        event.getId(),
                        event.getTitle(),
                        event.getDescription(),
                        event.getDate(),
                        event.getAddress() != null ? event.getAddress().getCity() : "",
                        event.getAddress() != null ? event.getAddress().getUf() : "",
                        event.getRemote(),
                        event.getEventUrl(),
                        event.getImgUrl()))
                .stream().toList();
    }

    public List<EventResponseDTO> getFilteredEvents(int page, int size, String title, String city, String uf, Date startDate, Date endDate) {
        Date effectiveStartDate = (startDate != null) ? startDate : new Date(0L);

        Date maxDate = Timestamp.valueOf(LocalDateTime.of(9999, 12, 31, 23, 59, 59));
        Date effectiveEndDate = (endDate != null) ? endDate : maxDate;
        Date currentDate = new Date();
        Pageable pageable = PageRequest.of(page, size);
        Page<Event> eventsPage = this.repository.findFilteredEvents(currentDate, title, city, uf, effectiveStartDate, effectiveEndDate, pageable);
        return eventsPage.map(event -> new EventResponseDTO(
                        event.getId(),
                        event.getTitle(),
                        event.getDescription(),
                        event.getDate(),
                        event.getAddress() != null ? event.getAddress().getCity() : "",
                        event.getAddress() != null ? event.getAddress().getUf() : "",
                        event.getRemote(),
                        event.getEventUrl(),
                        event.getImgUrl()))
                .stream().toList();
    }

    private String uploadImage(MultipartFile multipartFile){
        String fileName = UUID.randomUUID()+"-"+multipartFile.getOriginalFilename();

        try{
            File file = this.convertMultipartToFile(multipartFile);
            s3Client.putObject(bucketName, fileName, file);
            file.delete();
            return s3Client.getUrl(bucketName, fileName).toString();
        }catch (Exception e){
            // Imprime o stack trace para diagnóstico detalhado no console do servidor.
            e.printStackTrace();
            // Lança uma exceção para interromper o processo de criação do evento.
            throw new RuntimeException("Erro ao fazer upload da imagem", e);
        }
    }

    private File convertMultipartToFile(MultipartFile multipartFile) throws IOException {
        File convFile = new File(Objects.requireNonNull(multipartFile.getOriginalFilename()));
        FileOutputStream fos = new FileOutputStream(convFile);
        fos.write(multipartFile.getBytes());
        fos.close();
        return convFile;
    }
}
