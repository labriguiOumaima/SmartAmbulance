package ma.pfa.ambulance.dto;

import java.util.List;

public record Intervention(
        int id,
        Point ambulance,
        Point destination,
        int etaSeconds,
        List<Point> route
) {}