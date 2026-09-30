# SortableTableView AndroidX compatibility module

This Android-only module is based on
[`ISchwarz23/SortableTableView`](https://github.com/ISchwarz23/SortableTableView)
release `v2.8.1`, commit `29440ae44b33664d709c0bf50eda639a91c8ad97`.
The unmodified upstream license is retained in `LICENSE.txt`.

Radio-Oracle owns this compatibility copy because the published artifact still
references the retired `android.support` namespace. The source package, public
API, resources, and supported-device behavior remain compatible with version
2.8.1. Local changes are compatibility-only: AndroidX imports, type-safe bundle
restoration, removal of an unreachable pre-API-16 branch under Radio-Oracle's
minimum API 26, generic type cleanup, and current warning-clean build/test
metadata. This boundary lets the application remove Jetifier without combining
that toolchain migration with a rewrite of the Competitors table.
