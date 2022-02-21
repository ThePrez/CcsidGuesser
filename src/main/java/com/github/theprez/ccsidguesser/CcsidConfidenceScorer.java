package com.github.theprez.ccsidguesser;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public class CcsidConfidenceScorer {
    private final int m_sampleSize;

    public CcsidConfidenceScorer(final int _sampleSize) {
        m_sampleSize = _sampleSize;
    }

    public static class Confidence implements Comparable<Confidence> {

        private double m_confidence;
        private final int m_stringLen;

        public Confidence(final double numHits, final int _numChars, final int _stringLen) {
            m_confidence = (numHits) / (_numChars);
            m_stringLen = _stringLen;
        }

        @Override
        public int compareTo(final Confidence _o) {
            // if (m_stringLen < _o.m_stringLen) {
            // return 1;
            // } else
            if (m_confidence > _o.m_confidence) {
                return 1;
            } else if (m_confidence == _o.m_confidence) {
                return 0;
            }
            return -1;
        }

        public double getConfidence() {
            return m_confidence;
        }

        public int getLength() {
            return m_stringLen;
        }

        public void multiply(final double _m) {
            m_confidence *= Math.min(1, Math.max(0, _m));
        }
    }

    private Confidence getConfidence(final byte[] _data, final String _charsetName) {
        String s;
        try {
            final CharsetDecoder decoder = Charset.forName(_charsetName).newDecoder();
            decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
            final CharBuffer decoded = decoder.decode(ByteBuffer.wrap(_data));
            s = decoded.toString();
        } catch (final Exception e) {
            return new Confidence(0, 4 * _data.length, _data.length);
        }

        double numHits = 0;
        int numChars = 0;
        for (final char c : s.toCharArray()) {
            numHits += doesCharacterSeemValid(c);
            numChars++;
        }
        return new Confidence(numHits, numChars, s.length());
    }

    private double doesCharacterSeemValid(final char _c) {
        if ('\0' == _c) {
            return 1.0;
        }

        if (Character.isAlphabetic(_c)) {
            return 1.0;
        }
        if (Character.isWhitespace(_c)) {
            return 1.0;
        }
        if (Character.isDigit(_c)) {
            return 1.0;
        }

        final int type = Character.getType(_c);
        switch (type) {
            case Character.OTHER_PUNCTUATION:
            case Character.CONNECTOR_PUNCTUATION:
            case Character.CURRENCY_SYMBOL:
            case Character.OTHER_LETTER:
            case Character.OTHER_NUMBER:
                // case Character.OTHER_SYMBOL:
            case Character.FINAL_QUOTE_PUNCTUATION:
            case Character.DASH_PUNCTUATION:
            case Character.END_PUNCTUATION:
            case Character.MATH_SYMBOL:
            case Character.PARAGRAPH_SEPARATOR:
            case Character.SPACE_SEPARATOR:
            case Character.MODIFIER_LETTER:
            case Character.MODIFIER_SYMBOL:
                return 1.0;
        }
        if (type != Character.UNASSIGNED) {
            return 0.1;
        }
        final int cint = _c;
        return 0.0;
    }

    private final TreeMap<Confidence, LinkedList<String>> m_confidence = new TreeMap<Confidence, LinkedList<String>>();

    private void addConfidence(final String _charset, final Confidence hitrate) {
        LinkedList<String> entry = m_confidence.get(hitrate);
        if (null == entry) {
            entry = new LinkedList<String>();
            m_confidence.put(hitrate, entry);
        }
        for (final String confidenceEntry : entry) {
            if (confidenceEntry.equalsIgnoreCase(_charset)) {
                return;
            }
        }
        entry.add(_charset);
    }

    Map<Confidence, LinkedList<String>> getSortedData() {
        return m_confidence.descendingMap();
    }

    public String getBestGuess() {
        return getBestGuesses().getFirst();
    }

    public LinkedList<String> getBestGuesses() {
        final Entry<Confidence, LinkedList<String>> last = m_confidence.lastEntry();
        return last.getValue();
    }

    public void addCharset(final byte[] sampleData, final String charset, final double _multiplier) {
        final Confidence hitrate = getConfidence(sampleData, charset);
        hitrate.multiply(_multiplier);
        addConfidence(charset, hitrate);
    }

    public void addKnownCharset(final String _charset) {
        addConfidence(_charset, new Confidence(100, 100, 100));
    }
}
