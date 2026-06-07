# Course Analyzer

The Course Analyzer evaluates protected radio-orienteering course data after controls and route geometry have been imported for a category. It uses the course-design definitions from `Radio Orienteering Courses: Design and Set Guidelines, Rev 23-Mar-2024`.

## Definitions

- Provided route: the ideal route order supplied by the course setter, resolved against the category's assigned controls.
- Calculated ideal route: an independent route candidate generated from the start, finish, assigned foxes, beacon, and spectator if the category uses one.
- Horizontal distance: straight-line distance between route points, without elevation penalty.
- Route length: the imported route geometry length when a provided route exists; for calculated candidates, the straight-line distance through the calculated point order.
- Climb: positive elevation gain along the route.
- Effective length: route length plus ten times climb. For example, 5.00 km with 100 m of climb is 6.00 km effective length.

The analyzer report displays length values in kilometers to hundredths (`x.xx km`). Climb is displayed in meters with no decimal places. Analyzer time values omit a zero hours field, so `00:58:50` is displayed as `58:50`.

## Elevation Handling

When local Elevation Cache samples are complete for the relevant route or course points, the analyzer uses effective length as the primary comparison metric. If elevations are missing or incomplete, analysis still runs and falls back to horizontal distance. The report explains which metric was used in each section.

For the calculated ideal route, Radio-Oracle has no imported track line, so it samples each calculated straight leg at short intervals and applies cached elevation values to those samples. That sampled geometry is used for the calculated route's profile, climb, effective length, route comparison, and movement timing. If a cache value is unavailable for an intermediate sample, endpoint elevation interpolation is used when possible.

The Elevation Cache resolution setting is the spacing of Radio-Oracle's local sampling grid. For example, entering `3` meters creates an approximately 3-meter cache grid over the selected bounds. It does not guarantee that the upstream USGS source DEM is 3-meter data at every sampled point. Radio-Oracle samples the USGS 3DEP dynamic elevation service, which is based on multi-resolution DEM sources. USGS documents 1/9 arc-second, approximately 3-meter, DEM coverage as partial in the conterminous United States, while 1/3 arc-second, approximately 10-meter north/south, DEM coverage is the full-coverage seamless U.S. product. As a result, a 3-meter cache may contain closely spaced samples derived from coarser source data where 3-meter or better DEM coverage is not available.

Relevant USGS references:

- 3DEP products and services: https://www.usgs.gov/3d-elevation-program/about-3dep-products-services
- USGS DEM resolution FAQ: https://www.usgs.gov/faqs/what-projection-horizontal-datum-vertical-datum-and-resolution-a-usgs-digital-elevation-model
- USGS 3DEP dynamic elevation service: https://elevation.nationalmap.gov/arcgis/rest/services/3DEPElevation/ImageServer

## Speed Model

Estimated times use an elite-competitor baseline pace by race format: 4:38 min/km for Classic-style courses (3.6 m/s), 3:58 min/km for Sprint (4.2 m/s), and 4:54 min/km for Foxoring (3.4 m/s). The intent is to approximate an elite competitor on a clean runnable line, but the current implementation is not yet calibrated by category age or gender.

When elevation is available, movement time is based on effective length for each leg: horizontal length plus ten times positive climb. This same effective-length movement model is used for the provided route in Section 1 and for the calculated point-to-point route in Section 2. If elevation is incomplete, movement time falls back to horizontal distance. The current model does not include accumulated fatigue across the course.

For Classic-style fox controls, the timing model distinguishes arrival near the fox from departure after punching. If the competitor arrives while the fox is off the air, the analyzer assumes the competitor waits until that fox begins transmitting. It then adds a fixed 30 seconds to find and punch the fox before the competitor starts the next leg. If the competitor arrives while the fox is already transmitting, the same 30-second find-and-punch allowance is added from the arrival time.

## Report Sections

Section 1 appears only when a provided ideal route is available. It analyzes the supplied route order, imported route geometry, leg lengths, estimated splits, climb, effective length when available, and Classic wait times. Classic leg splits and cumulative times include any fox wait plus the 30-second find-and-punch allowance before the competitor departs for the next leg. Timing breakdown rows separate movement time, fox wait time, and find/punch allowance. Leg rows show the wait at the destination fox in parentheses after the cumulative time.

Section 1 also includes a separate wait-time analysis for Classic-style courses. It estimates arrival phase at each fox on the provided route and checks whether renumbering fox transmit slots could reduce total waiting. Candidate renumberings are evaluated by replaying the full route timing, so a wait at one fox changes the estimated arrival phase at later foxes. The report shows current wait, best wait, and the likely improvement. These wait estimates depend on the route and speed model; because map passability, fatigue, and category age/gender speed differences are not modeled, barriers, slow terrain, and competitor profile can shift real arrival times.

Section 2 always attempts to calculate an independent ideal route when enough course points are available. The analyzer exhaustively permutes assigned fox locations and any spectator point, keeps the beacon last before the finish, then selects the shortest candidate by effective length when elevations are complete or by horizontal distance otherwise. The calculated elevation profile is drawn from the sampled straight-leg geometry and marks fox locations with dots for comparison against the provided-route profile. For Classic-style courses, fox assignments are optimized for the calculated route to minimize wait time using the same wait-and-departure timing model. Because Section 2 can change fox assignments, a large ideal-time difference may come from reduced fox waiting rather than from route length alone; use the timing breakdown and wait rows to compare the cause.

Section 3 summarizes the results. When both a provided route and calculated candidate exist, it compares their order, distance metrics, estimated time, wait behavior, elevation profiles, and compact 2D route depictions. When no provided route exists, it summarizes only the calculated candidate.

The Course Analyzer export button writes the PDF report and a companion KML file with the same filename stem. The KML contains selectable folders for the provided foxes and route when a provided route is present, and for the calculated foxes and route. The calculated folder applies any improved Classic fox-numbering assignments recommended by the wait-time optimization.

## Algorithm Limits

The calculated route search is exhaustive and currently limited to eight permuted course points. This keeps the desktop UI responsive while covering normal Classic and Sprint category sizes. If a category exceeds that limit, the analyzer reports that the route calculation is too large rather than silently using a heuristic.

The analyzer does not currently import map passability or symbol constraints. It does not know about out-of-bounds areas, dense vegetation, lakes, uncrossable creeks and rivers, cliffs, fences, walls, or other barriers and impediments to on-foot navigation. This limits both ideal-route selection and Classic wait-time estimates, because the true best route and actual arrival phases can differ from straight-line or imported-route geometry when terrain forces detours or slows travel.
