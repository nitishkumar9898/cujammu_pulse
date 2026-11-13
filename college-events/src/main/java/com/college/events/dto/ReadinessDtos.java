package com.college.events.dto; import java.util.List;
public final class ReadinessDtos { private ReadinessDtos(){} public record Check(String key,String label,boolean passed,String detail){} public record View(long eventId,String status,boolean ready,List<Check> checks){} }
