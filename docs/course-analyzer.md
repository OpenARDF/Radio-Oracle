# Course Analyzer

The Course Analyzer evaluates protected radio-orienteering course data after controls and route geometry have been imported for a category. It uses the course-design definitions from `Radio Orienteering Courses: Design and Set Guidelines, Rev 23-Mar-2024`.

## Definitions

- Provided route: the ideal route order supplied by the course setter, resolved against the category's assigned controls.
- Calculated ideal route: an independent route candidate generated from the start, finish, assigned foxes, beacon, and spectator if the category uses one.
- Horizontal distance: straight-line distance between route points, without elevation penalty.
- Route length: the imported route geometry length when a provided route exists; for calculated candidates, the straight-line distance through the calculated point order.
- Climb: positive elevation gain along the route.
- Effective length: route length plus ten times climb. For example, 5.0 km with 100 m of climb is 6.0 km effective length.

## Elevation Handling

When local Elevation Cache samples are complete for the relevant route or course points, the analyzer uses effective length as the primary comparison metric. If elevations are missing or incomplete, analysis still runs and falls back to horizontal distance. The report explains which metric was used in each section.

The Elevation Cache resolution setting is the spacing of Radio-Oracle's local sampling grid. For example, entering `3` meters creates an approximately 3-meter cache grid over the selected bounds. It does not guarantee that the upstream USGS source DEM is 3-meter data at every sampled point. Radio-Oracle samples the USGS 3DEP dynamic elevation service, which is based on multi-resolution DEM sources. USGS documents 1/9 arc-second, approximately 3-meter, DEM coverage as partial in the conterminous United States, while 1/3 arc-second, approximately 10-meter north/south, DEM coverage is the full-coverage seamless U.S. product. As a result, a 3-meter cache may contain closely spaced samples derived from coarser source data where 3-meter or better DEM coverage is not available.

Relevant USGS references:

- 3DEP products and services: https://www.usgs.gov/3d-elevation-program/about-3dep-products-services
- USGS DEM resolution FAQ: https://www.usgs.gov/faqs/what-projection-horizontal-datum-vertical-datum-and-resolution-a-usgs-digital-elevation-model
- USGS 3DEP dynamic elevation service: https://elevation.nationalmap.gov/arcgis/rest/services/3DEPElevation/ImageServer

## Speed Model

Estimated times use an elite-competitor baseline speed by race format: 3.6 m/s for Classic-style courses, 4.2 m/s for Sprint, and 3.4 m/s for Foxoring. The intent is to approximate an elite competitor on a clean runnable line, but the current implementation is not yet calibrated by category age or gender.

Each leg is adjusted for elevation gradient. Uphill legs are slowed more than downhill legs, and the gradient penalty is clamped so extreme slopes do not produce unbounded speeds or penalties. The current model does not include accumulated fatigue across the course.

## Report Sections

Section 1 appears only when a provided ideal route is available. It analyzes the supplied route order, imported route geometry, leg lengths, estimated splits, climb, effective length when available, and Classic wait times.

Section 1 also includes a separate wait-time analysis for Classic-style courses. It estimates arrival phase at each fox on the provided route and checks whether renumbering fox transmit slots could reduce total waiting. The report shows current wait, best wait, and the likely improvement. These wait estimates depend on the route and speed model; because map passability, fatigue, and category age/gender speed differences are not modeled, barriers, slow terrain, and competitor profile can shift real arrival times.

Section 2 always attempts to calculate an independent ideal route when enough course points are available. The analyzer exhaustively permutes assigned fox locations and any spectator point, keeps the beacon last before the finish, then selects the shortest candidate by effective length when elevations are complete or by horizontal distance otherwise. For Classic-style courses, fox assignments are optimized for the calculated route to minimize wait time.

Section 3 summarizes the results. When both a provided route and calculated candidate exist, it compares their order, distance metrics, estimated time, wait behavior, elevation profiles, and compact 2D route depictions. When no provided route exists, it summarizes only the calculated candidate.

## Algorithm Limits

The calculated route search is exhaustive and currently limited to eight permuted course points. This keeps the desktop UI responsive while covering normal Classic and Sprint category sizes. If a category exceeds that limit, the analyzer reports that the route calculation is too large rather than silently using a heuristic.

The analyzer does not currently import map passability or symbol constraints. It does not know about out-of-bounds areas, dense vegetation, lakes, uncrossable creeks and rivers, cliffs, fences, walls, or other barriers and impediments to on-foot navigation. This limits both ideal-route selection and Classic wait-time estimates, because the true best route and actual arrival phases can differ from straight-line or imported-route geometry when terrain forces detours or slows travel.
