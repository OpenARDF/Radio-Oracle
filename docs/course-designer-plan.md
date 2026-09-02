# Course Designer Plan

Status: proposed medium-term initiative.

Last reviewed: 2026-09-02.

## Purpose

Radio-Oracle should gain a focused visual Course Designer for creating the
course files and race data that it currently imports from KML/KMZ, GPX, CSV,
and IOF CourseData XML. The editor should place controls and other geometry on
an offline georeferenced map, understand Radio-Oracle course semantics, and
write the result directly to a Race File or export it for interchange.

This is intentionally narrower than a general-purpose GIS. It is meant to
replace the Google Earth Pro course-authoring workflow, not reproduce every
Google Earth or QGIS capability.

The motivating planning assumption is that Google Earth Pro on desktop will
eventually become unsuitable as Radio-Oracle's primary authoring dependency.
The application currently reports that new desktop downloads will end on June
25, 2027. Existing installations may remain usable longer, which provides time
to build Course Designer incrementally and validate it against real events.

## Decision Summary

- Build Course Designer inside Radio-Oracle over a series of independently
  useful phases.
- Make `Course Design` a first-class item under `Setup`. After it reaches
  parity with the current controls workflow, it should replace the present
  top-level `Controls` group while retaining the controls table, imports,
  exports, elevation data, and destructive actions beneath it.
- Keep Google Earth Pro, QGIS, Purple Pen, OpenOrienteering Mapper, and similar
  applications as interim or specialist tools. QGIS is the preferred general
  open-source fallback while Course Designer is incomplete.
- Reuse Radio-Oracle's existing KML/KMZ reader, writer, style definitions,
  route-analysis rules, 2D rendering, and import-review behavior.
- Study Purple Pen for course-specific interaction and document-model ideas.
  Port only selected, license-compatible algorithms rather than its Windows UI
  or complete rendering engine.
- Study QGIS and OpenOrienteering Mapper for georeferencing, CRS, raster
  template, and file-format behavior. Reuse permissively licensed underlying
  components such as GDAL and PROJ when their benefits justify the native
  packaging cost; do not embed GPL application code while keeping
  Radio-Oracle's distributed application MIT-only.
- Start with JPG/PNG maps using world files, explicit coordinate information,
  or manual calibration. Treat robust GeoPDF support as a later and separate
  milestone.
- Keep the editable design in a Radio-Oracle document rather than using KML as
  the lossless working format. KML/KMZ remains an interchange and compatibility
  output.
- Do not include the base-map image in an exported KML/KMZ unless the operator
  explicitly requests a future map-inclusive export. The primary export
  contains only the controls, routes, and graphics authored over the map.

## Required User Workflow

Course Designer must support the following core workflow:

1. Open or create a Race File, or start a standalone course design.
2. Select an offline base map.
3. Use embedded georeferencing, a world file and CRS, known map coordinates,
   or manual calibration to position the map.
4. Place, name, and move control locations over the map.
5. Create category routes as LineStrings whose vertices snap to the controls.
6. Create simple display geometry such as independent lines, polygons, and
   circles.
7. Edit names, descriptions, visibility, colors, widths, fills, icons, and
   Radio-Oracle roles.
8. Preview exactly how the current Radio-Oracle importer, category matcher,
   Course Analyzer, and 2D Graphic tools will interpret the design.
9. Apply the design transactionally to the open Race File or export only the
   authored overlays as KML/KMZ, GPX, IOF CourseData XML, or other supported
   formats.
10. Save and reopen the editable design without losing base-map calibration,
    styles, circle definitions, object identity, or editor metadata.

The editor should remain useful without an internet connection. An online
basemap may be considered later, but it is not required for the Google Earth
Pro replacement workflow.

## Non-Goals

- Do not build a general-purpose GIS, map-symbol editor, or replacement for all
  of QGIS or OpenOrienteering Mapper.
- Do not require an online globe, online basemap, or external application for
  normal editing.
- Do not silently interpret decorative lines, circles, or polygons as Course
  Analyzer barriers, corridors, or terrain costs. Map-knowledge classification
  remains a separate explicit workflow.
- Do not make KML the lossless internal document or force the base map into the
  exported KMZ.
- Do not remove the current controls table, imports, exports, or Course Tools
  until the new workflow has proven parity.

## Proposed Product Placement

The eventual `Setup` navigation should be organized approximately as follows:

```text
Setup
|- Race File
|- Course Design
|  |- Design Course
|  |- Controls
|  |- Import
|  |- Export
|  `- Elevation Data
|- Categories
|- Competitors
|- Start List
`- More...
   `- Course Tools
```

`Design Course` should be the default Course Design workspace. The existing
controls table should remain available for exact numeric editing, SI numbers,
public labels, aliases, and fields that are more efficiently maintained in a
table.

The current `Controls` navigation group also owns elevation data, import,
export, course-overlay export, and `Delete All Controls`. Replacing the group
must therefore be a consolidation rather than the deletion of those
capabilities. See
[`DesktopNavigation.kt`](../desktopApp/src/main/kotlin/org/openardf/radiooracle/desktop/DesktopNavigation.kt)
and the existing `ControlDetailsPanel` wiring in
[`Main.kt`](../desktopApp/src/main/kotlin/org/openardf/radiooracle/desktop/Main.kt).

The present items under `More... > Course Tools` can be absorbed as Course
Designer reaches parity:

- `Create Course` becomes `New Course` in the designer.
- `Move Course` becomes a move-all or course-transform action.
- `Route Generator` becomes part of interactive route construction and
  category-route generation.
- `2D Graphic` becomes a preview/export surface backed by the current design.
- `Course Analyzer` can analyze the current draft or applied Race File data
  without another import step.
- `Course Report` may remain a separate report because it is a derived event
  output rather than an authoring operation.

During development, Course Designer should initially coexist with `Controls`
and the existing Course Tools. The navigation replacement should occur only
after the new surface can perform the normal control-entry, import, export, and
review workflows without forcing users back to the old group.

## Existing Radio-Oracle Foundation

The feature does not need a new KML or course subsystem. Existing components
already provide much of the non-interactive foundation:

- [`DesktopCourseFileReader.kt`](../desktopApp/src/main/kotlin/org/openardf/radiooracle/desktop/DesktopCourseFileReader.kt)
  reads KML/KMZ and recognizes points, LineStrings, polygons, visibility,
  descriptions, KML colors, line widths, and supported icon symbols.
- [`DesktopCourseKmlStyle.kt`](../desktopApp/src/main/kotlin/org/openardf/radiooracle/desktop/DesktopCourseKmlStyle.kt)
  defines the shared marker and KML style conventions.
- [`DesktopCreateCourseKml.kt`](../desktopApp/src/main/kotlin/org/openardf/radiooracle/desktop/DesktopCreateCourseKml.kt)
  creates starter Classic, Sprint, and Foxoring course KML and already reuses
  shared export primitives.
- [`DesktopControlsRouteKmlKmzExport.kt`](../desktopApp/src/main/kotlin/org/openardf/radiooracle/desktop/DesktopControlsRouteKmlKmzExport.kt)
  exports controls and route geometry to KML/KMZ and GPX.
- [`DesktopCourseGraphic.kt`](../desktopApp/src/main/kotlin/org/openardf/radiooracle/desktop/DesktopCourseGraphic.kt)
  and the Compose course canvases already render routes, polygons, styled
  lines, and marker shapes for 2D output and preview.
- The existing controls/route import review, protected-course unlock, Race File
  editing, Course Analyzer, course-type detection, validation, and export
  services should remain the source of truth instead of being reimplemented in
  the editor.

The missing layers are base-map loading and georeferencing, interactive
selection and editing, durable draft state, and product integration.

## External Applications And Component Reuse

### QGIS

QGIS is the strongest ready-now open-source replacement workflow. It can
georeference raster images, load many GeoPDFs through GDAL, create and edit
points, lines, polygons, circles, and other shapes, and export only selected
vector layers to KML/KMZ. The KML Tools plugin can preserve basic styles and
extract GroundOverlay images.

While Course Designer is incomplete, a repeatable QGIS project can use three
GeoPackage layers:

- `controls`: point geometry with name, description, icon, visibility, and
  optional Radio-Oracle role fields.
- `routes`: LineString geometry with the category name, color, and width.
- `graphics`: line or polygon geometry for circles, boundaries, and other 2D
  diagram objects.

The GeoPackage should be the editable master and KML/KMZ an export artifact.
Every export must be test-imported into Radio-Oracle because QGIS/GDAL round
trips can rewrite folders, styles, icon references, and extension data.

QGIS itself is C++/Qt and GPLv2-or-later. Its application code should not be
copied or linked into an otherwise MIT-only Radio-Oracle distribution without
a deliberate licensing decision. Its workflow and behavior remain useful
references, and the components beneath it are candidates in their own right.

References:

- [QGIS source and license](https://github.com/qgis/QGIS)
- [QGIS Georeferencer](https://docs.qgis.org/3.44/en/docs/user_manual/managing_data_source/georeferencer.html)
- [QGIS vector editing](https://docs.qgis.org/3.44/en/docs/user_manual/working_with_vector/editing_geometry_attributes.html)
- [QGIS KML Tools plugin](https://plugins.qgis.org/plugins/kmltools/)

### Purple Pen

Purple Pen most closely matches the desired human workflow. It displays OCAD,
OpenOrienteering Mapper, PDF, and bitmap maps; visually places controls and
course legs; supports undo/redo and special course objects; and exports KML.
Its project is listed under the permissive Simplified BSD License and is
written in C#.

Displaying a PDF does not by itself prove that Purple Pen consumes arbitrary
embedded GeoPDF georeferencing for KML output, and its documentation does not
establish that every decorative line, ellipse, or appearance choice is emitted
to KML. Treat those behaviors as test subjects, not inherited requirements or
confirmed implementation shortcuts.

Potentially useful areas to study and selectively port include:

- The course document and object model.
- Command-based undo/redo organization.
- Hit testing and selection priority for overlapping controls, lines, labels,
  and graphics.
- Moving shared controls while keeping connected legs consistent.
- Course-specific shapes, gaps, marked routes, and appearance rules.
- Live direct-manipulation behavior and course-information presentation.
- Coordinate conversion and tests around georeferenced OCAD/OOM input.

The C# Windows UI, GDI-oriented rendering, and PDFium wrapper will not drop
directly into Kotlin/Compose. Port only bounded algorithms or data-model ideas
that are simpler than an original Kotlin implementation, retain required
copyright notices, and verify the license of each source file used.

Purple Pen uses PDFium for PDF rendering. That validates PDFium as a capable
engine, but not necessarily as Radio-Oracle's best integration. A JVM-native
PDFBox implementation may be easier to package and maintain.

References:

- [Purple Pen](https://purple-pen.org/)
- [Purple Pen project, language, and license](https://launchpad.net/purple-pen)
- [Purple Pen change summary](https://purple-pen.org/change_summary.htm)

### OpenOrienteering Mapper

OpenOrienteering Mapper is a valuable reference for georeferenced image
templates, world files, map-template positioning, opacity and ordering, CRS
handling, GeoTIFF, and GDAL-supported raster/vector input. It exports KMZ and
simple-course KML but is fundamentally a mapmaking application rather than a
focused KML course editor.

OOM is C++/Qt and GPLv3. Its application code should be treated as a design
reference unless Radio-Oracle makes a deliberate GPL distribution decision.
Its use of GDAL and related libraries is more directly relevant than embedding
its editor or automating its GUI. The stock application does not provide a
supported headless conversion/export interface.

References:

- [OpenOrienteering Mapper source and license](https://github.com/OpenOrienteering/mapper)
- [Template positioning and georeferencing](https://www.openorienteering.org/mapper-manual/pages/templates.html)
- [Export formats](https://www.openorienteering.org/mapper-manual/pages/file_menu.html)
- [GDAL support in Mapper](https://github.com/OpenOrienteering/mapper-manual/blob/gh-pages/pages/gdal.md)

### ArcGIS Earth And Google Earth Web

ArcGIS Earth is the closest literal substitute for Google Earth Pro because it
can edit KML geometry and styling and create a ground overlay from a local
image. It remains a proprietary, primarily Windows-oriented contingency rather
than a component source for Radio-Oracle.

Google Earth Web can work with basic projects and existing KML, but it does not
provide the complete local image-overlay creation and manipulation workflow
needed here. It should not be treated as the successor to Google Earth Pro for
Course Designer inputs.

Reference:

- [ArcGIS Earth drawing and ground overlays](https://doc.arcgis.com/en/arcgis-earth/use/draw-and-measure.htm)

## Candidate Supporting Libraries

### GDAL

GDAL is the most comprehensive route to GeoTIFF, GeoPDF, raster metadata,
GroundOverlay extraction, reprojection, and broad geospatial format support.
GDAL is generally MIT-licensed, but a packaged binary can include dependencies
with different terms, so the complete distribution must be reviewed.

The principal cost is operational: Radio-Oracle would need compatible native
libraries and Java bindings in macOS, Windows, and Linux packages. GDAL should
sit behind a small geospatial service interface so it can be added or replaced
without coupling the editor model to a native API.

Reference: [GDAL license](https://gdal.org/en/stable/license.html).

### PROJ

PROJ is an MIT-licensed candidate for transforming projected map coordinates
and datums into WGS84 longitude/latitude. It also requires native packaging if
used through its normal C API. A pure-JVM projection implementation may cover
the initial WGS84 and UTM scope, with PROJ added when broader CRS fidelity is
required.

Reference: [PROJ overview and license](https://proj.org/en/stable/about.html).

### PDFBox And PDFium

Apache PDFBox is Apache-licensed, JVM-native, and can render PDF pages into
images. It is the preferred first experiment for ordinary PDF display. PDFBox
does not by itself make arbitrary GeoPDF interpretation trivial; Course
Designer would still need to read geospatial viewport/measure dictionaries,
choose a map neatline, and transform page coordinates correctly.

PDFium is a capable BSD-style-licensed renderer used by Purple Pen and Chromium.
It may render difficult PDFs more faithfully, but it introduces a native build
and binding problem similar to GDAL.

References:

- [Apache PDFBox](https://pdfbox.apache.org/)
- [PDFium source and license](https://github.com/chromium/pdfium)

### Initial Dependency Strategy

Do not add a full GIS stack for the first usable slice. Begin with:

- Existing Compose/Skia and JVM image support for JPG/PNG display.
- A small tested world-file reader. A world file is a six-value affine
  transformation, but the associated CRS must be supplied separately.
- Explicit WGS84 input and a bounded set of projected systems needed by actual
  Radio-Oracle maps, likely beginning with UTM.
- Manual calibration from known locations when metadata is absent or
  unreliable.
- Existing Radio-Oracle KML parsing and writing.

Perform a bounded GDAL/PROJ packaging spike before committing to GeoTIFF and
GeoPDF support in public distributables.

## Editable Document And Persistence

KML is not a sufficient lossless editor document. It does not naturally retain
all base-map calibration, editor state, circle center/radius semantics,
application object identities, validation state, or future course-specific
metadata.

Introduce a versioned `CourseDesignDocument` with at least:

- Document identity and schema version.
- Optional Race File/race identity link.
- Base-map path or bundled-resource reference.
- Selected PDF page when applicable.
- Base-map pixel dimensions, opacity, and display ordering.
- Source and target CRS identifiers.
- Pixel-to-map and map-to-WGS84 transformations or calibration control points.
- Course objects with stable IDs.
- Styles with explicit defaults rather than renderer-dependent values.
- Category-route associations.
- Source provenance, hashes, and import warnings.
- Optional editor viewport and layer visibility state.

A future `.rocourse` file can be a JSON document or a ZIP-based package. It
should support either a relative external map reference or an optional bundled
map for portability. Bundling a map in `.rocourse` is independent of the
normal KML/KMZ export rule: exported course overlays still omit the map unless
the user explicitly chooses otherwise.

When a Race File is open, pointer movements should edit an in-memory draft
rather than mutate the Race File continuously. `Apply to Race File` should use
one transaction and the existing review/protection policies. Cancel should
discard the draft, and undo after apply should eventually revert the entire
transaction in accordance with the planned Race Editing Model.

## Core Data Model

The model should distinguish authoring intent rather than reducing every object
immediately to an untyped KML Placemark:

- `ControlPoint`: stable ID, label/name, longitude/latitude, optional projected
  coordinate, elevation, description, visibility, icon, and Radio-Oracle role.
- `CategoryRoute`: category identity/name, ordered vertices, explicit snapped
  control identities, style, description, and optional route metadata.
- `GraphicLine`: independent line geometry and style that is not interpreted as
  a category route.
- `GraphicPolygon`: boundary/fill geometry and style.
- `GraphicCircle`: center and radius retained in the editable document and
  approximated as a polygon/LinearRing for KML export.
- `BaseMap`: source, page/band as applicable, georeferencing, opacity, and
  provenance.

Keep WGS84 coordinates available for every exportable course object. The
renderer may operate in projected map coordinates or pixels, but repeated
editing must not accumulate projection or screen-rounding error.

## Base-Map Support

### JPG And PNG

Initial support should accept:

- An image accompanied by a world file such as `.jgw`, `.pgw`, or `.wld`, plus
  an explicit or associated CRS.
- An image accompanied by known corner or control-point coordinates.
- An unreferenced image calibrated in Course Designer against two or more known
  locations. At least three well-distributed points should be encouraged when
  rotation, skew, or calibration error is possible.
- An image extracted from an existing KML/KMZ GroundOverlay with geographic
  bounds.

The calibration preview should show residual error and warn when control points
are poorly distributed or inconsistent.

### GeoTIFF

GeoTIFF should follow after the coordinate-transform abstraction is stable.
The implementation must read raster pixels, embedded CRS, affine transforms,
orientation, and nodata/transparency consistently. GDAL is the robust reference
implementation and likely long-term backend.

### PDF And GeoPDF

Ordinary PDF display and GeoPDF georeferencing are separate features:

- PDF rendering converts the selected page into a displayable raster at an
  appropriate resolution and supports progressive rerendering when zoomed.
- GeoPDF support extracts geographic viewport/measure information, coordinate
  systems, neatlines, rotations, and possibly multiple map frames or layers.
- A PDF that merely depicts a map must still be manually calibrated.

The first PDF milestone may render with PDFBox and require manual calibration.
Robust native GeoPDF recognition should be a later milestone, tested against
representative OCAD, Mapper, USGS, and other files actually used by operators.
External conversion to GeoTIFF through QGIS/GDAL remains an acceptable interim
workflow.

## Editor Interaction

The main workspace should provide:

- Smooth pan and zoom with scale and coordinate readouts.
- Base-map opacity control.
- Select, multi-select, move, and delete.
- Add control.
- Add category route and insert, move, or remove route vertices.
- Add independent line, polygon, and circle.
- Snap route vertices to controls and display the snap relationship.
- Properties inspector for name, role, description, visibility, icon, color,
  width, dash, fill, and category association.
- Layer/object list for selection, ordering, visibility, and locking.
- Keyboard deletion, escape/cancel, duplicate, and undo/redo.
- Move-all/transform operations for correcting systematic offsets.
- Import overlay and merge/review rather than silent replacement.
- Read-only and editable previews of what will be applied or exported.

Controls and routes should be easy to manipulate without requiring GIS
terminology. Present visible Radio-Oracle and sport terms rather than internal
model tokens.

## Radio-Oracle And KML Compatibility Rules

The designer has an important advantage over a generic GIS: it can validate the
exact subset that Radio-Oracle consumes.

- Point names and roles must remain recognizable to the shared course label and
  race-type rules.
- Category route names must map unambiguously to categories or receive an
  explicit association before apply/export.
- Ordinary route membership currently uses original LineString vertices.
  Merely passing a segment near or through a control is insufficient. Route
  creation and export must therefore snap and emit explicit vertices at the
  intended controls.
- A likely circular closed LineString is intentionally excluded as a category
  route by the current reader. Export authored circles as polygons with
  `LinearRing` geometry, not as route-like LineStrings. The current heuristic
  recognizes more than 20 vertices, endpoints within 20 meters, and perimeter
  greater than 80 meters; characterize this behavior in tests rather than
  coupling the editor UI to those thresholds.
- Preserve KML color conversion, width, visibility, descriptions, and supported
  icon conventions through the existing reader/writer and style helpers.
- The current icon parser recognizes `triangle`, `donut`, `target`, and
  `circle` in icon URLs. Normal exports should use the shared style definitions
  rather than depend on arbitrary external icon URLs surviving a round trip.
- Custom local icons require a KMZ resource or a stable external URL. Prefer
  built-in Radio-Oracle symbols for normal controls.
- Keep `description` and KML `ExtendedData` attached to features rather than
  pretending KML has native metadata for individual line segments.
- Continue using recognized course-object descriptions for existing per-leg
  `SS=#.##` behavior until a structured route metadata model is deliberately
  added.
- Export only the authored overlays by default. The base map remains a design
  reference and is not placed in the output KMZ.

The export preview should run the generated document back through
`DesktopCourseFileReader` and the applicable course importer. Validation should
compare stable object identities, names, coordinates, route membership,
styles, category mapping, and supported metadata before offering the file as a
trusted output.

## Race File Integration

Course Designer must support two modes:

### Standalone Mode

- Does not require an open Race File.
- Creates or opens a `.rocourse` design.
- Exports KML/KMZ and other interchange formats.
- Can later be attached or applied to a Race File through the normal review
  process.

### Race-Linked Mode

- Starts from the Race File's controls, category assignments, protected route
  geometry, roles, and style data.
- Allows editing in a draft while keeping the current Race File unchanged.
- Shows stale or conflicting mappings before apply.
- Requires the existing unlock path for protected control locations or course
  order where applicable.
- Applies the entire accepted design as one Race File transaction.
- Reuses existing pruning, duplicate detection, category matching, validation,
  and import-review behavior.

The base map may remain outside the Race File. If it is linked rather than
bundled, store a portable relative reference when possible and retain a source
hash so a moved or changed map can be detected.

## Implementation Sequence

### Phase 0: Characterization And Dependency Spikes

- Assemble small representative fixtures: world-file JPG/PNG, GeoTIFF,
  rotated/skewed image, KML GroundOverlay, ordinary PDF, and multiple GeoPDF
  encodings.
- Characterize the existing KML/KMZ round trip and importer expectations with
  focused tests before editor work changes them.
- Compare a pure-JVM image/CRS approach with a packaged GDAL/PROJ spike on
  macOS, Windows, and Linux.
- Evaluate PDFBox rendering and inspect the actual GeoPDF metadata found in
  Radio-Oracle users' maps.
- Review any Purple Pen source selected for porting at the file and license
  level before incorporating it.

### Phase 1: Read-Only Georeferenced Workspace

- Add `CourseDesignDocument`, versioning, and persistence.
- Load JPG/PNG plus world file or manual calibration.
- Implement the pixel, projected-map, and WGS84 transformation pipeline.
- Add pan, zoom, opacity, coordinate readout, and scale indication.
- Display an existing standalone KML/KMZ or Race File course over the map using
  the current Radio-Oracle renderer and style semantics.
- Provide calibration diagnostics and map/course extent checks.

This phase is independently useful for verifying existing course locations.

### Phase 2: Core Course Editing

- Add, select, move, rename, and delete controls.
- Create category LineStrings with control snapping and explicit membership.
- Add independent lines, polygons, and circles.
- Add the properties inspector and built-in icon choices.
- Add command-based undo/redo and keyboard interactions.
- Save/reopen `.rocourse` documents.
- Export KML/KMZ without the map and validate the generated output by
  re-importing it.

### Phase 3: Race File And Navigation Integration

- Open a design from current Race File controls and protected course data.
- Add transactional `Apply to Race File` with existing review, validation, and
  unlock behavior.
- Associate routes with categories explicitly and preview affected category
  assignments.
- Add `Setup > Course Design` alongside the existing `Controls` group.
- Fold `Create Course` and `Move Course` into Course Designer.
- After workflow parity and operator validation, replace the top-level
  `Controls` group with `Course Design` and retain the controls table and
  import/export/elevation submenus beneath it.

### Phase 4: Broader Raster And CRS Support

- Add GeoTIFF and broader CRS support through the selected backend.
- Add KML/KMZ GroundOverlay extraction and reopening.
- Harden high-resolution raster tiling, caching, and memory use.
- Validate packaged native components and licenses on all desktop targets if
  GDAL/PROJ is adopted.

### Phase 5: PDF And GeoPDF

- Render ordinary PDF pages and permit manual calibration.
- Add native GeoPDF metadata recognition, map-frame selection, neatlines, and
  CRS transformation.
- Validate representative real-world PDFs and graceful fallback to manual
  calibration.

### Phase 6: Course-Tool Consolidation

- Integrate Course Analyzer with the current draft.
- Integrate Route Generator behavior into interactive authoring.
- Integrate 2D Graphic preview/export.
- Remove redundant legacy entry points only after feature and output parity is
  demonstrated.

## Validation And Acceptance Criteria

Each phase should add focused tests and retain the normal repository validation
gates. Final acceptance requires:

- Known pixel locations transform to expected WGS84 coordinates within a
  documented tolerance.
- Forward and inverse transforms round-trip without visible drift.
- Rotated world files and non-north-up maps behave correctly.
- Pan/zoom and high-resolution images remain responsive on packaged desktop
  builds.
- Control movement updates snapped route membership predictably.
- Undo/redo restores geometry, styles, selection-relevant state, and category
  associations.
- Exported KML/KMZ re-imports into Radio-Oracle with matching controls, routes,
  graphic polygons, styles, visibility, and supported descriptions.
- Circles do not become category routes.
- The generated file contains no base-map image unless a future explicit
  map-inclusive option is selected.
- Standalone editing works without a Race File.
- Race-linked apply is transactional and respects protected-course safeguards.
- Existing CSV, KML/KMZ, GPX, IOF CourseData, controls-table, elevation, and
  course-analysis workflows remain available through the new navigation.
- macOS, Windows, and Linux packages include every required runtime component
  and attribution, with no dependency on a locally installed QGIS, OOM, GDAL,
  or command-line tool unless an optional external-conversion workflow is
  explicitly selected.

Use synthetic calibration and geometry fixtures first, then validate against
representative real courses and compare locations with Google Earth Pro and
QGIS. A source-only coordinate check is insufficient; packaged-app visual and
round-trip evidence is required before replacing the current Controls workflow.

## Risks And Open Decisions

- Whether `.rocourse` should be JSON, a ZIP package, or an extension of the
  existing Race File envelope.
- Whether the base map should normally be linked, copied into the event working
  folder, or optionally embedded in `.rocourse`.
- Which projected coordinate systems are required before PROJ/GDAL becomes
  necessary.
- Whether a pure-JVM GeoTIFF implementation is sufficient or a native GDAL
  runtime is warranted.
- Whether PDFBox rendering covers actual event maps well enough or PDFium is
  needed.
- How much GeoPDF metadata variation must be supported before the feature is
  labeled native GeoPDF rather than PDF plus calibration.
- Whether free graphics belong only in the design document or also require a
  durable representation in Race Files and protected course data.
- How imported KML objects that are outside Radio-Oracle's supported subset are
  preserved and disclosed on re-export.
- How concurrent changes to a linked Race File and an open course draft are
  reconciled.
- Whether future online basemaps are desirable and, if so, how licensing,
  caching, privacy, and offline operation are handled.

Resolve these through bounded spikes and explicit decisions rather than
allowing implementation details to become accidental file-format or product
contracts.
