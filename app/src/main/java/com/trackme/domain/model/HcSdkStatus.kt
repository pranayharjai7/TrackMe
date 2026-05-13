package com.trackme.domain.model

/**
 * Domain model describing Health Connect SDK availability.
 *
 * Architecture Layer: Domain model
 *
 * Responsibilities:
 * - Keep Health Connect availability state independent from Android data classes.
 * - Let ViewModels depend on stable domain language instead of data-layer types.
 */
enum class HcSdkStatus {
    AVAILABLE,
    NEEDS_UPDATE,
    NEEDS_INSTALL,
}
