package com.college.events.dto;

/** Public directory projection; institutional staff contact details are not public directory data. */
public final class DepartmentViews {
    private DepartmentViews() {}

    public record DirectoryItem(long id, String code, String name, String building) {
        public static DirectoryItem from(com.college.events.model.Department department) {
            return new DirectoryItem(department.getId(), department.getCode(), department.getName(), department.getBuilding());
        }
    }
}
