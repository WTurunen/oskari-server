package org.oskari.wcs.coverage;

import org.oskari.wcs.gml.Envelope;

public abstract class CoverageDescription {

    private final String coverageId;
    private final Envelope boundedBy;
    private final String nativeFormat;

    public CoverageDescription(String coverageId, Envelope boundedBy, String nativeFormat) {
        this.coverageId = coverageId;
        this.boundedBy = boundedBy;
        this.nativeFormat = nativeFormat;
    }

    public String getCoverageId() {
        return coverageId;
    }

    public Envelope getBoundedBy() {
        return boundedBy;
    }

    public String getNativeFormat() {
        return nativeFormat;
    }

    public abstract boolean hasAxis(String axis);

}
