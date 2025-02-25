// TODO complete README.md
open module eu.dariolucia.ccsds.cfdp {
    requires java.logging;
    requires transitive java.xml.bind;
    requires eu.dariolucia.ccsds.tmtc;

    exports eu.dariolucia.ccsds.cfdp.common;
    exports eu.dariolucia.ccsds.cfdp.protocol.checksum;
    exports eu.dariolucia.ccsds.cfdp.protocol.decoder;
    exports eu.dariolucia.ccsds.cfdp.protocol.pdu;
    exports eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs;
    exports eu.dariolucia.ccsds.cfdp.mib;
    exports eu.dariolucia.ccsds.cfdp.entity;
    exports eu.dariolucia.ccsds.cfdp.entity.util;
    exports eu.dariolucia.ccsds.cfdp.entity.indication;
    exports eu.dariolucia.ccsds.cfdp.entity.request;
    exports eu.dariolucia.ccsds.cfdp.entity.segmenters;
    exports eu.dariolucia.ccsds.cfdp.ut;
    exports eu.dariolucia.ccsds.cfdp.ut.impl;
    exports eu.dariolucia.ccsds.cfdp.filestore;
    exports eu.dariolucia.ccsds.cfdp.filestore.impl;

    uses eu.dariolucia.ccsds.cfdp.protocol.checksum.ICfdpChecksumFactory;

}