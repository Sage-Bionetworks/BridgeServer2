package org.sagebionetworks.bridge.models.upload;

/** Enum that specifies the exporter version to be used for the app. */
public enum ExporterVersion {
    /** The old exporter, which exports nightly to Synapse tables. */
    LEGACY_EXPORTER,

    /** Exporter 3.0, which exports to Synapse FileEntities with annotations. */
    EXPORTER_3,
}
