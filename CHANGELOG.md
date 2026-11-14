# Changelog of `property-notification-api`

Every release of this repository, newest first. One release per course cut, tagged on `main`
(`git-conventions.md`, "Tags and versioning"; SemVer). Each entry lists the user stories it
brought, with the pull requests of `develop` that built them.

## [3.0.0] - 2026-11-15

### Added

- **HU-NOTIF-002 (code-corhuila/property-docs#198) — Choose notification channels**
  - chore(notification): start the service in three modules (#1)
  - chore(notification): answer with the common contract (#2)
  - feat(notification): validate the token on every route under /api (#3)
  - feat(notification): store the notification preferences (#4)
  - feat(notification): read and change the notification preferences (#5)
- **HU-NOTIF-001 (code-corhuila/property-docs#201) — Notify the guest of the outcome of a reservation**
  - feat(notification): decide what to tell the guest of a reservation (#6)
  - feat(notification): record, render and send the e-mail (#7)
  - feat(notification): consume notification.reservas in a process of its own (#8)
