# Radio-Oracle 1.0.54

## Guided race and series setup

- Add a guided desktop workflow for creating a Race File, importing IOF course data, or organizing races into a Race Series.
- Keep the active Race File or Race Series visible in a persistent top selector and route related navigation through that selector.
- Stage imported IOF data for review, clarify unmatched or ambiguous controls, and preserve the existing Race File when an import is rejected.
- Use clearer descriptive labels for Sprint fast controls in user-facing course and import views.
- Show where course elevation data is missing so organizers can identify the terrain data they still need.

## Race-day changes

- Review a control's SI station assignment before the race, including when course coordinates are incomplete. Android now provides this review in Categories.
- Review competitor category assignments after the first registration and switch between races in a series from the Android race toolbar.
- New custom Practice categories default to Open. Standard age and gender categories keep their usual defaults.

## Platform and release reliability

- Update jDeploy and its archive dependencies while retaining the verified Java 17 desktop runtime.
- Add installed-package coverage for Windows and Linux ARM64 alongside the existing x64 lanes.
- Strengthen protected release and evidence handling so application changes and release records return through reviewed pull requests.
- Update Gradle to the security-patched 9.8.1 release and retain only the two documented upstream plugin-warning waivers.

## Android release notes

Switch between races from the race toolbar. In Categories, review competitor assignments and change a control’s SI station before the race. New custom Practice categories default to Open. Course imports and Sprint fast-control labels are clearer.

## Release validation

Further physical-device and SPORTident testing was waived by the release owner for 1.0.54.

The isolated Android unit-test manifest may report two specifically waived Firebase permission-removal notices because Firebase Measurement is absent from that test merge. The final packaged manifest is checked instead and must exclude both advertising-ID permissions. Mockito test workers may also report that class-data sharing is unavailable after Mockito appends their bootstrap classpath; this affects only test-VM startup optimization. Gradle 9.8.1 exposes two specifically waived upstream plugin deprecations: AGP's `Configuration.setVisible(boolean)` call and Compose Hot Reload's `HotReloadUsageType` attribute. Dependency-version availability notices remain in the separate Renovate maintenance lane. These notices are not Radio-Oracle source warnings.

## Standards compatibility

The upstream ARDF JSON schema at `83eeaa413c6b0b7a0564247b45a1d93cfd5fc3c1` defines `category_gender` as a Boolean. Radio-Oracle uses an optional `category_open` flag to preserve mixed-gender categories in race and result JSON. The standards proposal for this field is pending. IOF XML 3.0 already defines the corresponding Class sex value `B`.
