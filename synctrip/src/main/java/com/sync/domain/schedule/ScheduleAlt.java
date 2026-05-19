package com.sync.domain.schedule;

import com.sync.domain.band.Band;
import com.sync.domain.place.Place;
import com.sync.domain.place.PlaceCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "schedule_alts")
public class ScheduleAlt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_alt_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private Band band;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private PlaceCategory category;

    @Column(name = "density_point", nullable = false)
    private int densityPoint;

    @Column(name = "priority_score", nullable = false)
    private float priorityScore;

    protected ScheduleAlt() {}

    private ScheduleAlt(Band band, Place place, float priorityScore) {
        this.band = band;
        this.place = place;
        this.category = place.getCategory();
        this.densityPoint = place.getDensityPoint();
        this.priorityScore = priorityScore;
    }

    public static ScheduleAlt create(Band band, Place place, float priorityScore) {
        return new ScheduleAlt(band, place, priorityScore);
    }

    public Long getId() { return id; }
    public Band getBand() { return band; }
    public Place getPlace() { return place; }
    public PlaceCategory getCategory() { return category; }
    public int getDensityPoint() { return densityPoint; }
    public float getPriorityScore() { return priorityScore; }
}
