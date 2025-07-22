package com.eventostec.api.repositories;

import com.eventostec.api.domain.event.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    @Query("SELECT e FROM Event e LEFT JOIN e.address a WHERE e.date >= :currentDate")
    public Page<Event> findUpcommingEvents(@Param("currentDate")Date currentDate, Pageable pageable);

    @Query("SELECT e FROM Event e " +
            "LEFT JOIN e.address a " +
            "WHERE e.date >= :currentDate " +
            "AND LOWER(e.title) LIKE LOWER(CONCAT('%', COALESCE(:title, ''), '%')) " +
            "AND (a.city IS NULL OR LOWER(a.city) LIKE LOWER(CONCAT('%', COALESCE(:city, ''), '%'))) " +
            "AND (a.uf IS NULL OR LOWER(a.uf) LIKE LOWER(CONCAT('%', COALESCE(:uf, ''), '%'))) " +
            "AND e.date >= :startDate " +
            "AND e.date <= :endDate")
    public Page<Event> findFilteredEvents(
            @Param("currentDate") Date currentDate,
            @Param("title") String title,
            @Param("city") String city,
            @Param("uf") String uf,
            @Param("startDate") Date startDate,
            @Param("endDate") Date endDate,
            Pageable pageable
    );

}
