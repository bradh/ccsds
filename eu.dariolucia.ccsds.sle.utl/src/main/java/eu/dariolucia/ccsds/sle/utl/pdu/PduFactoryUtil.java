/*
 *  Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.sle.utl.pdu;

import com.beanit.jasn1.ber.ReverseByteArrayOutputStream;
import com.beanit.jasn1.ber.types.BerInteger;
import com.beanit.jasn1.ber.types.BerNull;
import com.beanit.jasn1.ber.types.BerObjectIdentifier;
import com.beanit.jasn1.ber.types.BerOctetString;
import com.beanit.jasn1.ber.types.string.BerVisibleString;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.ConditionalTime;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.Credentials;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.Time;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.isp1.credentials.HashInput;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.isp1.credentials.ISP1Credentials;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.service.instance.id.OidValues;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.service.instance.id.ServiceInstanceAttribute;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.service.instance.id.ServiceInstanceIdentifier;
import eu.dariolucia.ccsds.sle.utl.config.network.RemotePeer;
import eu.dariolucia.ccsds.sle.utl.si.ApplicationIdentifierEnum;
import eu.dariolucia.ccsds.sle.utl.si.HashFunctionEnum;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for the credentials and time construction and verification.
 */
public class PduFactoryUtil {

    private static final Logger LOG = Logger.getLogger(PduFactoryUtil.class.getName());

    private PduFactoryUtil() {
        // Private constructor
    }

    /**
     * Number of days from 1st Jan 1958 to 1st Jan 1970
     */
    private static final int DAYS_FROM_1958_TO_1970;

    static {
        GregorianCalendar d1958 = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        d1958.set(1958, Calendar.JANUARY, 1, 0, 0);
        GregorianCalendar d1970 = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        d1970.set(1970, Calendar.JANUARY, 1, 0, 0);

        Instant i1958 = d1958.toInstant();
        Instant i1970 = d1970.toInstant();
        Duration d = Duration.between(i1958, i1970);
        DAYS_FROM_1958_TO_1970 = (int) d.toDays();
    }

    /**
     * This method builds the service instance identifier object starting from its string representation.
     *
     * @param serviceInstanceIdentifier the SIID as string
     * @param type                      the service type
     * @return the {@link ServiceInstanceIdentifier} object representing the provided string
     */
    public static ServiceInstanceIdentifier buildServiceInstanceIdentifier(final String serviceInstanceIdentifier,
                                                                           final ApplicationIdentifierEnum type) {
        if (LOG.isLoggable(Level.FINER)) {
            LOG.finer("Building SIID from string " + serviceInstanceIdentifier + ", service " + type);
        }
        String[] dotSplit = serviceInstanceIdentifier.split("\\.", -1);
        // Assume 4 entries
        ServiceInstanceIdentifier toReturn = new ServiceInstanceIdentifier();

        String sagr = dotSplit[0].split("=", -1)[1];
        addSiAttribute(toReturn, OidValues.sagr, sagr);


        String spack = dotSplit[1].split("=", -1)[1];
        addSiAttribute(toReturn, OidValues.spack, spack);

        String svtt = dotSplit[2].split("=", -1)[1];
        switch (type) {
            case RAF:
            case RCF:
            case ROCF:
                addSiAttribute(toReturn, OidValues.rslFg, svtt);
                break;
            case FSP:
            case CLTU:
                addSiAttribute(toReturn, OidValues.fslFg, svtt);
                break;
            default:
                throw new IllegalArgumentException("Service type " + type + " unknown");
        }

        String fs = dotSplit[3].split("=", -1)[1];
        switch (type) {
            case RAF:
                addSiAttribute(toReturn, OidValues.raf, fs);
                break;
            case RCF:
                addSiAttribute(toReturn, OidValues.rcf, fs);
                break;
            case ROCF:
                addSiAttribute(toReturn, OidValues.rocf, fs);
                break;
            case FSP:
                addSiAttribute(toReturn, OidValues.fsp, fs);
                break;
            case CLTU:
                addSiAttribute(toReturn, OidValues.cltu, fs);
                break;
            default:
                throw new IllegalArgumentException("Service type " + type + " unknown");
        }

        return toReturn;
    }

    private static void addSiAttribute(ServiceInstanceIdentifier si, BerObjectIdentifier oid, String value) {
        ServiceInstanceAttribute attribute = new ServiceInstanceAttribute();
        ServiceInstanceAttribute.SEQUENCE theSequence = new ServiceInstanceAttribute.SEQUENCE();
        theSequence.setIdentifier(oid);
        theSequence.setSiAttributeValue(new BerVisibleString(value));
        attribute.getSEQUENCE().add(theSequence);
        si.getServiceInstanceAttribute().add(attribute);
    }

    /**
     * This method constructs an empty Credentials object, i.e. credentials are not used.
     *
     * @return the Credentials object
     */
    public static Credentials buildEmptyCredentials() {
        return buildCredentials(false, null, null, HashFunctionEnum.SHA_1);
    }

    /**
     * This method generates the ASN.1 Credentials object.
     *
     * @param fillCredentials whether the credentials must be filled or not (BerNull)
     * @param username        the username to be used in the credentials data
     * @param password        the password
     * @param hashToUse       the has function to use (usually, from SLE version 1 to 3: hash using SHA-1, 20 bytes; 4-5: hash using SHA-256, 32 bytes)
     * @return the Credentials object fully populated
     */
    public static Credentials buildCredentials(boolean fillCredentials, String username, byte[] password, HashFunctionEnum hashToUse) {
        Credentials c = new Credentials();
        if (fillCredentials) {
            // Current time, as per CCSDS 913.1-B-2, 3.1.2.1.1
            long time = System.currentTimeMillis();
            // Random number (positive), as per CCSDS 913.1-B-2, 3.1.2.1.1
            long randomNumber = (new Random(time).nextInt()) & 0x7FFFFFFFL;
            // No support for microsecond resolution
            byte[] buffer = hashCredentialsData(time, 0, randomNumber, username, password);
            // The next variable is what the standard calls 'the protected'
            byte[] hashSignature = calculateTheProtected(hashToUse, buffer);
            // Build the ISP1 Credentials object
            ISP1Credentials isp1Credentials = new ISP1Credentials();
            // Set the time: CDS with implicit P-Field, epoch 1st Jan 1958, microseconds resolution
            // (ref. CCSDS 913.1-B-2)
            isp1Credentials.setTime(new BerOctetString(buildCDSTime(time, 0)));
            // Set the protected credentials data (hashed)
            isp1Credentials.setTheProtected(new BerOctetString(hashSignature));
            // Set the random number
            isp1Credentials.setRandomNumber(new BerInteger(randomNumber));
            // Encode
            ReverseByteArrayOutputStream encoding = new ReverseByteArrayOutputStream(140, true);
            try {
                isp1Credentials.encode(encoding, true);
                encoding.close();
                byte[] encoded = encoding.getArray();
                c.setUsed(new BerOctetString(encoded));
            } catch (IOException e) {
                throw new IllegalStateException("Credential encoding failed", e);
            }
        } else {
            c.setUnused(new BerNull());
        }
        return c;
    }

    private static byte[] calculateTheProtected(HashFunctionEnum hashToUse, byte[] buffer) {
        byte[] hashSignature;// Compute the hash signature of the credentials data, ref. CCSDS 913.1-B-2 3.1.2.1.3
        try {
            MessageDigest md = MessageDigest.getInstance(hashToUse.getHashFunction());
            md.reset();
            md.update(buffer);
            hashSignature = md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Hash function not defined: " + hashToUse.getHashFunction(), e);
        }
        return hashSignature;
    }

    /**
     * This method encodes the credentials data as specified in CCSDS 913.1-B-2, 3.1.2.1.2 using the DER
     * encoding rules as per ISO/IEC 8825-1:2008 (revised version is 2015).
     * <p>
     * HashInput ::= SEQUENCE
     * { time OCTET STRING (SIZE(8))
     * , randomNumber INTEGER (0 .. 2147483647)
     * , userName VisibleString
     * , passWord OCTET STRING
     * }
     * <p>
     * According to the JASN.1 library documentation, the output of the JASN.1 encoder should be DER-compliant (not
     * verified).
     *
     * @param timeMillis   time in millisecs since Java epoch
     * @param microsec     microseconds in the millisecond
     * @param randomNumber the random number
     * @param username     the username
     * @param password     the password (as byte array)
     * @return the encoded information using DER
     */
    private static byte[] hashCredentialsData(long timeMillis, long microsec, long randomNumber, String username,
                                              byte[] password) {
        try {
            HashInput hashInput = new HashInput();
            hashInput.setTime(new BerOctetString(buildCDSTime(timeMillis, microsec)));
            hashInput.setRandomNumber(new BerInteger(randomNumber));
            hashInput.setUserName(new BerVisibleString(username));
            hashInput.setPassWord(new BerOctetString(password));
            ReverseByteArrayOutputStream os = new ReverseByteArrayOutputStream(140, true);
            hashInput.encode(os); // by default calls encode(os, true)
            os.close();
            return os.getArray();
        } catch (IOException e) {
            throw new IllegalStateException("HashInput creation failed", e);
        }
    }

    /**
     * This method builds the CDS time representation according to the SLE standard:
     * <ul>
     * <li>P-field is implicit</li>
     * <li>T-field:</li>
     * <li>2 octets: number of days since 1958/01/01 00:00:00</li>
     * <li>4 octets: number of milliseconds of the day</li>
     * <li>2 octets: number of microseconds of the millisecond (set to 0 if not used)</li>
     * <li>This definition reflects exactly the format of the CCSDS defined time tag as used in spacelink data units.</li>
     * </ul>
     * Reference for implementation: CCSDS 301.0-B-4, section 3.3.
     *
     * @param timeMillisSinceEpoch time in millisecs since Java epoch
     * @param microsecsInMillisec  microseconds in the millisecond
     * @return the CDS (8 bytes) representation of the provided time
     */
    public static byte[] buildCDSTime(long timeMillisSinceEpoch, long microsecsInMillisec) {
        // Guard condition to reject negative values
        if (microsecsInMillisec < 0 || timeMillisSinceEpoch < 0) {
            throw new IllegalArgumentException("Negative value provided: " + timeMillisSinceEpoch + ", " + microsecsInMillisec);
        }
        // Compute the number of seconds from Java epoch
        long secs = timeMillisSinceEpoch / 1000;
        // Compute the number of days from Java epoch and, to be compliant with the CCSDS epoch (1st Jan 1958)
        // add DAYS_FROM_1958_to_1970 days. DAYS_FROM_1958_to_1970 is the difference from the two epochs dates
        // (1st Jan 1970 - 1st Jan 1958)
        long daysFromEpoch = secs / 86400 + DAYS_FROM_1958_TO_1970;
        // Now compute the milliseconds within the day: number of seconds in the day (remainder) times 1000 plus the
        // remainder of the milliseconds
        long millisecsInDay = (secs % 86400) * 1000 + timeMillisSinceEpoch % 1000;
        // if microseconds is not normalised (i.e. > 999) normalize it and add the result to the milliseconds
        millisecsInDay += microsecsInMillisec / 1000;
        // Compute the number of microseconds within the millisecond (normalized)
        microsecsInMillisec %= 1000;
        // Finally, encode the result using a ByteBuffer, MSB by default in Java, integers are truncated
        ByteBuffer bb = ByteBuffer.allocate(8);
        bb.putShort((short) daysFromEpoch);
        bb.putInt((int) millisecsInDay);
        bb.putShort((short) microsecsInMillisec);
        return bb.array();
    }

    /**
     * This method builds the CDS time representation according to the SLE standard:
     * <ul>
     * <li>P-field is implicit</li>
     * <li>T-field:</li>
     * <li>2 octets: number of days since 1958/01/01 00:00:00</li>
     * <li>4 octets: number of milliseconds of the day</li>
     * <li>4 octets: number of picoseconds of the millisecond (set to 0 if not used)</li>
     * <li>This definition reflects exactly the format of the CCSDS defined time tag as used in spacelink data units.</li>
     * </ul>
     * Reference for implementation: CCSDS 301.0-B-4, section 3.3.
     *
     * @param timeMillisSinceEpoch time in millisecs since Java epoch
     * @param picosecsInMillisec  picoseconds in the millisecond
     * @return the CDS (8 bytes) representation of the provided time
     */
    public static byte[] buildCDSTimePico(long timeMillisSinceEpoch, long picosecsInMillisec) {
        // Guard condition to reject negative values
        if (picosecsInMillisec < 0 || timeMillisSinceEpoch < 0) {
            throw new IllegalArgumentException("Negative value provided: " + timeMillisSinceEpoch + ", " + picosecsInMillisec);
        }
        // Compute the number of seconds from Java epoch
        long secs = timeMillisSinceEpoch / 1000;
        // Compute the number of days from Java epoch and, to be compliant with the CCSDS epoch (1st Jan 1958)
        // add DAYS_FROM_1958_to_1970 days. DAYS_FROM_1958_to_1970 is the difference from the two epochs dates
        // (1st Jan 1970 - 1st Jan 1958)
        long daysFromEpoch = secs / 86400 + DAYS_FROM_1958_TO_1970;
        // Now compute the milliseconds within the day: number of seconds in the day (remainder) times 1000 plus the
        // remainder of the milliseconds
        long millisecsInDay = (secs % 86400) * 1000 + timeMillisSinceEpoch % 1000;
        // if microseconds is not normalised (i.e. > 999) normalize it and add the result to the milliseconds
        millisecsInDay += picosecsInMillisec / 1000000000;
        // Compute the number of microseconds within the millisecond (normalized)
        picosecsInMillisec %= 1000000000;
        // Finally, encode the result using a ByteBuffer, MSB by default in Java, integers are truncated
        ByteBuffer bb = ByteBuffer.allocate(10);
        bb.putShort((short) daysFromEpoch);
        bb.putInt((int) millisecsInDay);
        bb.putInt((int) picosecsInMillisec);
        return bb.array();
    }

    /**
     * This method performs the authentication on the basis of the received credentials from the expected user,
     * according to the specification detailed in CCSDS 913.1-B-2, 3.1.2.2.2-4.
     *
     * @param remotePeer         the remote peer
     * @param encodedCredentials the received credentials (to be BER-decoded)
     * @param authDelayInSeconds the maximum delay in seconds to accept the credentials as good
     * @return true if the authentication succeeds, false otherwise
     */
    public static boolean performAuthentication(RemotePeer remotePeer, byte[] encodedCredentials, int authDelayInSeconds) {
        // First: decode the credentials
        ISP1Credentials isp1Credentials = new ISP1Credentials();
        ByteArrayInputStream in = new ByteArrayInputStream(encodedCredentials);
        try {
            isp1Credentials.decode(in, true);
        } catch (IOException e) {
            LOG.log(Level.WARNING, String.format("Cannot decode credentials from remote peer %s, encoded credentials are %s", remotePeer.getId(), DatatypeConverter.printHexBinary(encodedCredentials)), e);
            return false;
        }
        // From the Credentials time, we extract the time
        long[] timeMillis;
        try {
            timeMillis = buildTimeMillis(isp1Credentials.getTime().value);
        } catch (IllegalArgumentException e) {
            if(LOG.isLoggable(Level.WARNING)) {
                LOG.warning(String.format("Cannot read time from credentials of remote peer %s, CDS time is %s", remotePeer.getId(), DatatypeConverter.printHexBinary(isp1Credentials.getTime().value)));
            }
            return false;
        }
        // We get the current time, that we use to compute the delay. If above the configured threshold we reject the
        // invocation.
        long now = System.currentTimeMillis();
        if (now - timeMillis[0] > authDelayInSeconds * 1000) {
            if(LOG.isLoggable(Level.WARNING)) {
                LOG.warning(String.format("Cannot verify credentials of remote peer %s, acceptable delay exceeded, now=%d, time=%d, acceptable delay in ms=%d", remotePeer.getId(), now, timeMillis[0], authDelayInSeconds * 1000));
            }
            return false;
        }
        // Now we check the hash of the credentials data: we compare the provided protected with the one
        // we compute locally. If they are equals, all fine.

        // Local hash using random number and time from the ISP1Credentials object, and locally stored user and pass
        byte[] localCredentialsData = hashCredentialsData(timeMillis[0], timeMillis[1],
                isp1Credentials.getRandomNumber().longValue(), remotePeer.getId(), remotePeer.getPassword());
        // Compute the hash signature
        byte[] hashSignature = calculateTheProtected(remotePeer.getAuthenticationHash(), localCredentialsData);
        // Comparison
        return Arrays.equals(hashSignature, isp1Credentials.getTheProtected().value);
    }

    /**
     * Returns an array with, at index 0, the number of milliseconds since 1970, at
     * index 1, the number of microsecs within the millisec.
     *
     * @param cdsTime the time in CDS format, implicit P-field, 8 bytes
     * @return the result
     * @throws IllegalArgumentException if the number of days is fewer than 1958-1970 difference
     */
    public static long[] buildTimeMillis(byte[] cdsTime) {
        ByteBuffer bb = ByteBuffer.wrap(cdsTime);
        int days = Short.toUnsignedInt(bb.getShort());
        long millisec = Integer.toUnsignedLong(bb.getInt());
        int microsec = Short.toUnsignedInt(bb.getShort());
        // To move to the Java epoch, remove DAYS_FROM_1958_to_1970 days
        if (days < DAYS_FROM_1958_TO_1970) {
            throw new IllegalArgumentException("Provided CDS time returns a number of days fewer than 1958-1970 difference: " + PduStringUtil.toHexDump(cdsTime));
        }
        days -= DAYS_FROM_1958_TO_1970;
        return new long[]{days * 86400L * 1000L + millisec, microsec};
    }

    /**
     * Returns an array with, at index 0, the number of milliseconds since 1970, at
     * index 1, the number of picosecs within the millisec.
     *
     * @param cdsTime the time in CDS format, implicit P-field, 10 bytes
     * @return the result
     * @throws IllegalArgumentException if the number of days is fewer than 1958-1970 difference
     */
    public static long[] buildTimeMillisPico(byte[] cdsTime) {
        ByteBuffer bb = ByteBuffer.wrap(cdsTime);
        int days = Short.toUnsignedInt(bb.getShort());
        long millisec = Integer.toUnsignedLong(bb.getInt());
        long picosec = Integer.toUnsignedLong(bb.getInt());

        if (days < DAYS_FROM_1958_TO_1970) {
            throw new IllegalArgumentException("Provided CDS pico-time returns a number of days fewer than 1958-1970 difference: " + PduStringUtil.toHexDump(cdsTime));
        }
        days -= DAYS_FROM_1958_TO_1970;
        return new long[]{days * 86400L * 1000L + millisec, picosec};
    }

    /**
     * Maps the provided ConditionalTime object into a Java Date, or null if the time is not set.
     *
     * @param theTime the condition time to convert
     * @return the corresponding Date object
     */
    public static Date toDate(ConditionalTime theTime) {
        Instant instant = toInstant(theTime);
        return instant == null ? null : new Date(instant.toEpochMilli());
    }

    /**
     * Maps the provided ConditionalTime object into a Java Instant, or null if the time is not set.
     *
     * @param theTime the condition time to convert
     * @return the corresponding Instant object
     */
    public static Instant toInstant(ConditionalTime theTime) {
        if(theTime == null || theTime.getUndefined() != null) {
            return null;
        } else {
            Time t = theTime.getKnown();
            if(t.getCcsdsFormat() != null) {
                // Millisecond resolution
                long[] components = buildTimeMillis(t.getCcsdsFormat().value);
                return Instant.ofEpochMilli(components[0]);
            } else if(t.getCcsdsPicoFormat() != null) {
                // Picosecond resolution
                long[] components = buildTimeMillisPico(t.getCcsdsPicoFormat().value);
                return Instant.ofEpochSecond(components[0]/1000, (components[1] % 1000) * 1000000L + components[0]/1000);
            } else {
                // Problem
                throw new IllegalArgumentException("ConditionalTime does not deliver any time!");
            }
        }
    }
}
