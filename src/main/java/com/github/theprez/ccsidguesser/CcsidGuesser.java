package com.github.theprez.ccsidguesser;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.SortedMap;

import com.github.theprez.ccsidguesser.CcsidConfidenceScorer.Confidence;
import com.github.theprez.jcmdutils.AppLogger;
import com.github.theprez.jcmdutils.ProcessLauncher;
import com.github.theprez.jcmdutils.StringUtils;

public class CcsidGuesser {

    enum ConvertMode {
        DOTUTF8, INPLACE, NONE
    }

    enum OutputFormat {
        CCSID, CONFIDENCE, ENC;

        public void print(final PrintStream _out, final String _charset, final double _confidence, final int _ccsid) {
            if (CCSID == this) {
                _out.println("" + _ccsid);
            } else if (ENC == this) {
                _out.println("" + _ccsid + " (" + _charset + ")");
            } else if (CONFIDENCE == this) {
                final String msg = String.format("%d  (%s) confidence=%02f", _ccsid, _charset, _confidence);
                _out.println(msg);
            }
        }
    }

    private static final int SHOW_ALL = -1;
    private static final int SHOW_TIES_ONLY = -2;
    public static final byte[] UTF16BE_BOM = { (byte) 0xfe, (byte) 0xff };
    public static final byte[] UTF16LE_BOM = { (byte) 0xff, (byte) 0xfe };

    public static final byte[] UTF32BE_BOM = { (byte) 0x00, (byte) 0x00, (byte) 0xfe, (byte) 0xff };

    public static final byte[] UTF32LE_BOM = { (byte) 0x00, (byte) 0x00, (byte) 0xff, (byte) 0xfe };
    public static final byte[] UTF8_BOM = { (byte) 0xef, (byte) 0xbb, (byte) 0xbf };

    private static void convertFileToUTF8(final AppLogger _logger, final ConvertMode convertMode, final int _ccsid, final File _file) throws IOException, InterruptedException {
        InputStreamReader in = null;
        OutputStreamWriter out = null;
        final File tagFile;
        try {
            if (ConvertMode.NONE == convertMode) {
                return;
            } else if (ConvertMode.DOTUTF8 == convertMode) {
                in = new InputStreamReader(new BufferedInputStream(new FileInputStream(_file), 1024 * 1024), CcsidUtils.ccsidToEncoding(_ccsid, true));
                final File utf8File = new File(_file.getAbsolutePath() + ".utf8");
                out = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(utf8File), 1024 * 1024), "UTF-8");
                tagFile = utf8File;
            } else {
                final File destFile = new File(_file.getAbsolutePath());
                final File backupFile = new File(_file.getAbsolutePath() + ".bak");
                if (!_file.renameTo(backupFile)) {
                    throw new IOException("Unable to create backup file " + backupFile);
                }

                in = new InputStreamReader(new BufferedInputStream(new FileInputStream(backupFile), 1024 * 1024), CcsidUtils.ccsidToEncoding(_ccsid, true));
                out = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(destFile), 1024 * 1024), "UTF-8");
                tagFile = destFile;
            }
            final char[] cbuf = new char[1024 * 1024];
            int bytesRead = -1;
            while (-1 != (bytesRead = in.read(cbuf))) {
                out.write(cbuf, 0, bytesRead);
            }
            setCcsidTag(_logger, tagFile, 1208);
            _logger.println_success("Conversion to UTF-8 complete!");
        } finally {
            if (null != in) {
                in.close();
            }
            if (null != out) {
                out.close();
            }
        }
    }

    private static Entry<Integer, String> getTaggedCcsidAndEncoding(final AppLogger _logger, final File _file) {
        if (!isIBMi()) {
            return null;
        }
        try {
            final Process p = Runtime.getRuntime().exec(new String[] { "/QOpenSys/usr/bin/attr", _file.getAbsolutePath(), "ccsid" });
            String output = "";
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"))) {
                String line;
                while (null != (line = br.readLine())) {
                    output += line + "\n";
                }
            }
            output = output.trim();
            _logger.println_verbose("attr rc=" + p.waitFor());
            final int ccsid = Integer.valueOf(output);
            final String encoding = CcsidUtils.ccsidToEncoding(ccsid, true);
            _logger.printfln_verbose("INFO: currently-tagged encoding is %s (ccsid %d)", encoding, ccsid);
            final HashMap<Integer, String> ccsidAndEncodingMap = new HashMap<Integer, String>();
            ccsidAndEncodingMap.put(ccsid, encoding);
            for (final Entry<Integer, String> ret : ccsidAndEncodingMap.entrySet()) {
                return ret;
            }
        } catch (final Exception e) {
            _logger.printExceptionStack_verbose(e);
        }
        return null;
    }

    private static boolean isIBMi() {
        return System.getProperty("os.name", "Misty").matches("(?i)OS/?400");
    }

    public static void main(final String[] _args) {

        final LinkedList<String> args = new LinkedList<String>();
        args.addAll(Arrays.asList(_args));

        final AppLogger logger = AppLogger.getSingleton(args.remove("-v"));
        String file = null;
        boolean autofix = false;
        ConvertMode convertMode = ConvertMode.NONE;
        OutputFormat outputFormat = OutputFormat.CCSID;
        int showNum = 1;
        int sampleSize = 1024 * 128;

        for (final String remainingArg : args) {
            if (remainingArg.toLowerCase().startsWith("--format=")) {
                try {
                    outputFormat = OutputFormat.valueOf(remainingArg.replaceFirst(".*=", "").toUpperCase());
                } catch (final Exception e) {
                    logger.println_err("ERROR: invalid output format specifier");
                }
            } else if (remainingArg.toLowerCase().startsWith("--convert=")) {
                try {
                    convertMode = ConvertMode.valueOf(remainingArg.replaceFirst(".*=", "").toUpperCase());
                } catch (final Exception e) {
                    logger.println_err("ERROR: invalid conversion mode specifier");
                }
            } else if (remainingArg.toLowerCase().startsWith("--show=")) {
                final String val = remainingArg.replaceFirst(".*=", "");
                if (val.equalsIgnoreCase("ALL")) {
                    showNum = SHOW_ALL;
                } else if (val.equalsIgnoreCase("top")) {
                    showNum = SHOW_TIES_ONLY;
                } else {
                    try {
                        showNum = Integer.valueOf(val.toLowerCase().replace("top", ""));
                    } catch (final Exception e) {
                        logger.println_err("ERROR: invalid argument: " + remainingArg);
                        printUsageAndExit();
                    }
                }
            } else if (remainingArg.equalsIgnoreCase("--sample-size=")) {
                final String val = remainingArg.replaceFirst(".*=", "");
                try {
                    sampleSize = 1024 * Integer.valueOf(val);
                } catch (final Exception e) {
                    logger.println_err("ERROR: invalid argument: " + remainingArg);
                    printUsageAndExit();
                }
            } else if (remainingArg.equalsIgnoreCase("--autofix")) {
                autofix = true;
            } else if (remainingArg.equalsIgnoreCase("--help") || remainingArg.equalsIgnoreCase("-h")) {
                printUsageAndExit();
            } else if (!remainingArg.startsWith("-")) {
                if (null != file) {
                    logger.println_err("ERROR: Only one file at a time is supported");
                    printUsageAndExit();
                }
                file = remainingArg;
            } else {
                logger.println_warn("WARNING: Argument '" + remainingArg + "' unrecognized and will be ignored");
            }
        }
        if (null == file) {
            logger.println_err("ERROR: No file specified");
            printUsageAndExit();
        }
        final CcsidConfidenceScorer tracker = new CcsidConfidenceScorer(sampleSize);
        try (FileInputStream fis = new FileInputStream(file)) {
            final byte[] sampleData = new byte[sampleSize];
            Arrays.fill(sampleData, (byte) 0x00);
            final int bytesRead = fis.read(sampleData);
            if (0 >= bytesRead) {
                logger.println_err("ERROR: unable to read file");
                throw new IOException("Unable to read file");
            }
            fis.close();
            final SortedMap<String, Charset> supportedCharsets = Charset.availableCharsets();
            final LinkedHashSet<String> ccsidList = new LinkedHashSet<String>();
            final String[] preferredCcsids = new String[] { "UTF8", "ISO8859_1", "ISO8859_2", "ISO8859_3", "ISO8859_4", "ISO8859_5", "ISO8859_6", "ISO8859_7", "ISO8859_8", "ISO8859_9", "ISO8859_10", "ISO8859_11", "ISO8859_12", "ISO8859_14", "Cp1250", "Cp1251", "Cp1252", "Cp1253", "Cp1254", "Cp1255", "Cp1256", "Cp1257",
                    "Cp037", "Cp1140", "Cp273", "Cp1141", "Cp277", "Cp1142", "Cp278", "Cp1143", "Cp280", "Cp1144", "Cp284", "Cp1145", "Cp285", "Cp1146", "Cp297", "Cp1147", "Cp500", "Cp1148", "Cp871", "Cp1149", "Cp1047", "Cp924" };
            final Entry<Integer, String> taggedEncoding = getTaggedCcsidAndEncoding(logger, new File(file));
            if (null != taggedEncoding && StringUtils.isNonEmpty(taggedEncoding.getValue())) {
                ccsidList.add(taggedEncoding.getValue());
            }
            ccsidList.addAll(Arrays.asList(preferredCcsids));
            ccsidList.addAll(supportedCharsets.keySet());

            logger.println_verbose("CCSID preference list ---> " + StringUtils.arrayToSpaceSeparatedString(ccsidList.toArray(new String[0])));
            String bomEncoding = null;
            if (startsWith(sampleData, UTF8_BOM)) {
                bomEncoding = "UTF8";
                ccsidList.remove("UTF-8");
            } else if (startsWith(sampleData, UTF16BE_BOM)) {
                bomEncoding = "UTF-16BE";
            } else if (startsWith(sampleData, UTF16LE_BOM)) {
                bomEncoding = "UTF-16LE";
            } else if (startsWith(sampleData, UTF32LE_BOM)) {
                bomEncoding = "UTF-32LE";
            } else if (startsWith(sampleData, UTF32BE_BOM)) {
                bomEncoding = "UTF-32BE";
            }
            if (null != bomEncoding) {
                tracker.addKnownCharset(bomEncoding);
                ccsidList.remove(bomEncoding);
                final Charset cs = Charset.forName(bomEncoding);
                for (final String alias : cs.aliases()) {
                    ccsidList.remove(alias);
                }
            }
            for (final String charset : ccsidList) {
                if (-1 == CcsidUtils.unknownStringToCCSID(charset)) {
                    continue;
                }
                tracker.addCharset(sampleData, charset, null == bomEncoding ? 1.00 : 0.50);
            }

            int printed = 0;
            int topGuess = -1;
            for (final Entry<Confidence, LinkedList<String>> entry : tracker.getSortedData().entrySet()) {
                final LinkedList<String> charsets = entry.getValue();
                final Confidence confidence = entry.getKey();
                if (confidence.getConfidence() <= 0.0003) {
                    break;
                }
                for (final String charset : charsets) {
                    if (printed >= showNum && showNum >= 0) {
                        break;
                    }
                    final int ccsid = CcsidUtils.unknownStringToCCSID(charset);
                    outputFormat.print(System.out, charset, 100 * confidence.getConfidence(), ccsid);
                    if (-1 == topGuess) {
                        topGuess = ccsid;
                    }
                    printed++;
                }
                if (SHOW_TIES_ONLY == showNum) {
                    break;
                }
            }
            if (-1 == topGuess) {
                throw new RuntimeException("ERROR: Unable to guess CCSID!");
            }
            logger.println_verbose("top guess was " + topGuess);
            if (autofix) {
                setCcsidTag(logger, new File(file), topGuess);
            }
            if (ConvertMode.NONE != convertMode) {
                if (1208 == topGuess) {
                    logger.println("File already looks like UTF-8");
                } else {
                    convertFileToUTF8(logger, convertMode, topGuess, new File(file));
                }
            }

        } catch (final Exception e) {
            logger.printExceptionStack_verbose(e);
            logger.println_err(e.getLocalizedMessage());
        }
    }

    private static void printUsageAndExit() {
        // @formatter:off
        final String autoFixOpt = isIBMi()?"        --autofix            automatically and unapologetically change the CCSID tag of the file\n                             to match the top guess for the file's contents (IBM i only)\n":"";
        final String usage = "Usage: java -jar ccsidguesser.jar  [options] <file>\n"
                                + "\n"
                                + "    Valid options include:\n"
                                + "        --show=top/topN/all: how many CCSID guesses to show (default is 'top1'), which shows the\n"
                                + "                             top 1 result. A value of 'top' shows the top guess and some number\n"
                                + "                             of very-near guesses.\n"
                                + "        --format=<format>:   output format (default is 'ccsid'). See valid formats below.\n"
                                + "        --convert=<mode>:    convert file to UTF-8 (default is 'none'). See valid modes below.\n"
                                + autoFixOpt
                                + "\n"
                                + "    Valid formats include:\n"
                                + "        ccsid:        Show the CCSID only\n"
                                + "        enc:          Show the CCSID and encoding name\n"
                               // + "        confidence:   Show the CCSID, encoding name, and confidence level\n"
                               + "\n"
                               + "    Valid convert modes include:\n"
                               + "         none :        perform no conversion\n"
                               + "         inplace:      Convert the file in-place (creates a .bak with the old contents)\n"
                               + "         dotutf8:      Create a new file that is UTF-8 (extension will be .utf8)\n"
                                + "\n"
                                ;
        // @formatter:on
        System.err.println(usage);
        System.exit(-1);
    }

    private static void setCcsidTag(final AppLogger _logger, final File _file, final int _ccsid) throws IOException, InterruptedException {
        if (!isIBMi() || 0 > _ccsid) {
            _logger.println_verbose("Skipping setting of CCSID tag");
        }
        _logger.printfln_verbose("Trying to set CCSID of file '%s' to " + _ccsid);
        final Process p = Runtime.getRuntime().exec(new String[] { "/QOpenSys/usr/bin/setccsid", "" + _ccsid, _file.getAbsolutePath() });
        ProcessLauncher.pipeStreamsToCurrentProcess("SETCCSID", p, _logger);
        _logger.println_verbose("CCSID set rc=" + p.waitFor());
    }

    private static boolean startsWith(final byte[] _b, final byte[] _comp) {
        try {
            for (int i = 0; i < _comp.length; ++i) {
                if (_b[i] != _comp[i]) {
                    return false;
                }
            }
        } catch (final IndexOutOfBoundsException _e) {
            return false;
        }
        return true;
    }
}
