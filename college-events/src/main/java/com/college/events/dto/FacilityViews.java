package com.college.events.dto;

/** Public catalogue projection; operational maintenance controls stay private. */
public final class FacilityViews {
    private FacilityViews() {}

    public record CatalogItem(long id, String name, String facilityType, String location, Integer capacity) {
        public static CatalogItem from(com.college.events.model.Facility facility) {
            return new CatalogItem(facility.getId(), facility.getName(), facility.getFacilityType(),
                    facility.getLocation(), facility.getCapacity());
        }
    }
}
