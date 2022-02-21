
package com.github.theprez.ccsidguesser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;

import com.ibm.as400.access.ConversionMaps;
import com.ibm.as400.access.NLS;

public class CcsidUtils {
    /**
     * Contains a copy of the toolbox's encoding-to-CCSID map, except with typesafety built in for accessors of
     * this map. This map has the following advantages over the toolbox map:
     * <ul>
     * <li>All keys are lowercased, so lookups are expected to be done with lowercase strings. This is because the JDK names don't always case-match Toolbox names.
     * <li>The values are integers. The toolbox map uses string representations of integers.
     * <li>We can easily add more as we discover them. The toolbox, unsurprisingly, misses some (see comment in code in static initializer for that list)
     * </ul>
     */
    private static final Map<String, Integer> g_toolboxEncodingMap;
    static {
        g_toolboxEncodingMap = new Hashtable<>();
        // load the toolbox's lookup table into our hashmap with type-safety (and do it on static init so the reflection is only needed once)
        try {
            final Map<?, ?> cpMap = ConversionMaps.encodingCcsid_;
            for (final Entry<?, ?> entry : cpMap.entrySet()) {
                try {
                    g_toolboxEncodingMap.put(entry.getKey().toString().toLowerCase(), Integer.valueOf(entry.getValue().toString()));
                } catch (final NumberFormatException _e) {
                    // if it happens we don't care so not even bothering to log, move along, nothing to see here
                }
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
        // TODO: add more here
        // Could perhaps use the info here:
        // http://www-01.ibm.com/software/globalization/ccsid/ccsid_registered.html
        // the current list of unknowns (after hardcodes below) seems to be:
        // Big5-HKSCS
        // hp-roman8
        // IBM1047_LF
        // IBM1141_LF
        // IBM924_LF
        // ISO-2022-CN
        // ISO-2022-JP-2
        // ISO-8859-10
        // ISO-8859-13
        // ISO-8859-14
        // ISO-8859-16
        // KOI8-U
        // PTCP154
        // x-Big5-Solaris
        // x-compound-text
        // x-EUC_JP_LINUX
        // x-eucJP-Open
        // x-IBM-udcJP
        // x-IBM1046S
        // x-IBM1362
        // x-IBM1363
        // x-IBM1363C
        // x-IBM1390A
        // x-IBM1399A
        // x-IBM33722C
        // x-IBM420S
        // x-IBM808
        // x-IBM833
        // x-IBM859
        // x-IBM864S
        // x-IBM930A
        // x-IBM939A
        // x-IBM943C
        // x-IBM949C
        // x-IBM954C
        // x-ISCII91
        // x-ISO-2022-CN-CNS
        // x-ISO-2022-CN-GB
        // x-iso-8859-11
        // x-ISO-8859-6S
        // x-JISAutoDetect
        // x-KOI8_RU
        // x-MacArabic
        // x-MacDingbat
        // x-MacHebrew
        // x-MacSymbol
        // x-MacThai
        // x-MacUkraine
        // x-MS932_0213
        // x-MS950-HKSCS
        // x-PCK
        // x-SJIS_0213
        // X-UTF-32BE-BOM
        // X-UTF-32LE-BOM
        // x-UTF_8J
        // x-windows-1256S
        // x-windows-50220
        // x-windows-50221
        // x-windows-iso2022jp
        // Found CCSID for 168/227 charsets
        // (when running Java 6)

        g_toolboxEncodingMap.put("CESU-8".toLowerCase(), 9400);
        g_toolboxEncodingMap.put("UTF-16LE".toLowerCase(), 1202);
        g_toolboxEncodingMap.put("UTF-16BE".toLowerCase(), 1200);
        g_toolboxEncodingMap.put("UTF-16".toLowerCase(), 1204);
        g_toolboxEncodingMap.put("UTF-32".toLowerCase(), 1236);
        g_toolboxEncodingMap.put("UTF-32BE".toLowerCase(), 1232);
        g_toolboxEncodingMap.put("UTF-32LE".toLowerCase(), 1234);
        g_toolboxEncodingMap.put("UTF-32BE-bom".toLowerCase(), 1232);
        g_toolboxEncodingMap.put("UTF-32LE-bom".toLowerCase(), 1234);
        g_toolboxEncodingMap.put("x-EUC-TW".toLowerCase(), 964);
        g_toolboxEncodingMap.put("x-EUC-KR".toLowerCase(), 970);
        g_toolboxEncodingMap.put("Big5-HKSCS".toLowerCase(), 1375);
        g_toolboxEncodingMap.put("Big5-HKSCS-2001".toLowerCase(), 1375);
        g_toolboxEncodingMap.put("x-windows-iso2022jp".toLowerCase(), 5054);
        g_toolboxEncodingMap.put("x-euc-jp-linux".toLowerCase(), 954);
        g_toolboxEncodingMap.put("x-euc-jp-Open".toLowerCase(), 954);
    }

    public static Charset ccsidToCharset(final int _ccsid) throws UnsupportedCharsetException {
        try {
            return Charset.forName(ccsidToEncodingViaToolboxAndFallback(_ccsid));
        } catch (final Exception e) {
            // fall thru to try the ccsid-as-CharSet-name case
        }
        // The above technique misses a few that can be picked up by simply looking up the CCSID#
        // These include:
        // 5601=EUC-KR
        // 646=US-ASCII
        // and in the future, there may be more.
        return Charset.forName("" + _ccsid);
    }

    /**
     * Pass false for the fallback arg (2nd) if you want validation
     *
     * @param _ccsid
     *            the CCSID
     * @param _fallbackToGenericCpXXXX
     *            true if one wants a valid String regardless
     * @return String holding equivalent java encoding
     * @throws UnsupportedCharsetException
     *             if no match and !_fallbackToGenericCpXXXX
     * @throws IOException
     *             when InputStream close() fails
     */
    public static String ccsidToEncoding(final int _ccsid, final boolean _fallbackToGenericCpXXXX) throws UnsupportedCharsetException, IOException {
        try {
            // this weird two-step guarantees that the JVM understands the returned value
            return charsetToEncoding(ccsidToCharset(Integer.valueOf(_ccsid)));
        } catch (final UnsupportedCharsetException e) {
            if (!_fallbackToGenericCpXXXX) {
                throw e;
            }
            return ccsidToEncodingViaToolboxAndFallback(_ccsid);
        }
    }

    private static String ccsidToEncodingViaToolboxAndFallback(final int _ccsid) {
        final String enc = NLS.ccsidToEncoding(_ccsid);
        return null == enc ? "Cp" + _ccsid : enc;
    }

    /**
     * Gets a matching ccsid for the given charset
     *
     * @param _c
     *            charset for which to locate the matching ccsid
     * @return a ccsid, or <tt>-1</tt> on failure
     * @throws IOException
     *             on charset close()
     */
    public static int charsetToCCSID(final Charset _c) throws IOException {
        if (null == _c) {
            return -1;
        }
        int ccsid = encodingToCCSID(_c.name());
        if (0 < ccsid) {
            return ccsid;
        }
        ccsid = encodingToCCSID(charsetToEncoding(_c));
        if (0 < ccsid) {
            return ccsid;
        }
        for (final String s : _c.aliases()) {
            ccsid = encodingToCCSID(s);
            if (0 < ccsid) {
                return ccsid;
            }
        }
        return -1;
    }

    /**
     * Returns an encoding string for the given charset.
     *
     * @param _c
     *            charset for which to locate the matching encoding
     * @return matching encoding String
     * @throws IOException
     *             when close() has trouble
     * @throws NullPointerException
     *             if parm is null
     */
    public static String charsetToEncoding(final Charset _c) throws IOException {
        try (InputStream is = new ByteArrayInputStream(new byte[0])) {
            return new InputStreamReader(is, _c).getEncoding();
        }
    }

    /**
     *
     * @param _encoding
     *            An encoding name. Or, sometimes, charset names work, too!
     * @return -1 on failure
     */
    public static int encodingToCCSID(final String _encoding) {
        int ccsid = NLS.encodingToCCSID(_encoding); // this is a case-sensitive lookup
        if (0 < ccsid) {
            return ccsid;
        }
        ccsid = NLS.encodingToCCSID(_encoding.replace("ISO-", "ISO").replace("-", "_")); // this is a case-sensitive lookup
        if (0 < ccsid) {
            return ccsid;
        }
        try {
            // Some names already contain the exact mapping to codepage. "Cp____" and "IBM____" always seem to have a ccsid of ____
            return Integer.valueOf(_encoding.replaceFirst("^(Cp|IBM|x-IBM|windows-|x-windows-)", ""));
        } catch (final NumberFormatException _e) {
            // if it happens we'll fall thru and try the lookup so no log here
        }
        final Integer lookedUp = g_toolboxEncodingMap.get(_encoding.toLowerCase()); // case-insensitive. All values of the map are lowercased
        return null == lookedUp ? -1 : lookedUp;
    }

    public static Charset encodingToCharset(final String _encoding) throws UnsupportedCharsetException {
        return Charset.forName(_encoding);
    }

    public static int toCCSID(final String _charset) {
        try {
            return encodingToCCSID(unknownStringToEncoding(_charset, "unknown"));
        } catch (final Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static int unknownStringToCCSID(final String _enc) {
        try {
            if (0 == _enc.trim().length()) {
                throw new RuntimeException(new UnsupportedEncodingException());
            }
            final int ccsid = encodingToCCSID(_enc.trim());
            if (-1 != ccsid) {
                return ccsid;
            }
            return charsetToCCSID(Charset.forName(_enc.trim()));
        } catch (final Exception e) {
            return -1;
        }
    }

    public static String unknownStringToEncoding(final String _enc, final String _fallback) throws UnsupportedCharsetException, IOException {
        if (0 == _enc.trim().length()) {
            if (null == _fallback) {
                throw new RuntimeException(new UnsupportedEncodingException());
            }
            return _fallback;
        }
        final int ccsid = encodingToCCSID(_enc.trim());
        if (-1 != ccsid) {
            return ccsidToEncoding(ccsid, true);
        }
        try {
            return charsetToEncoding(Charset.forName(_enc.trim()));
        } catch (final Exception _e) {
            if (null == _fallback) {
                throw new RuntimeException(new UnsupportedEncodingException(_enc));
            }
            // TODO: it's not a charset name, not an encoding name, not a CCSID #, what is it?
            return _fallback;
        }
    }

}
