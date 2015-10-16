package biweekly.io.text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

/*
 Copyright (c) 2013-2015, Michael Angstadt
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met: 

 1. Redistributions of source code must retain the above copyright notice, this
 list of conditions and the following disclaimer. 
 2. Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution. 

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * Reads lines of text from a reader, transparently unfolding lines that are
 * folded.
 * @author Michael Angstadt
 */
public class FoldedLineReader extends BufferedReader {
	/**
	 * Regular expression used to detect the first line of folded,
	 * "quoted-printable" property values.
	 */
	private final Pattern foldedQuotedPrintableValueRegex = Pattern.compile("[^:]*?QUOTED-PRINTABLE.*?:.*?=", Pattern.CASE_INSENSITIVE);

	private String lastLine;
	private boolean singleSpaceFolding = true;
	private int lastLineNum = 0, lineCount = 0;
	private final Charset charset;

	/**
	 * Creates a folded line reader.
	 * @param reader the reader object to wrap
	 */
	public FoldedLineReader(Reader reader) {
		super(reader);

		if (reader instanceof InputStreamReader) {
			InputStreamReader isr = (InputStreamReader) reader;
			String charsetStr = isr.getEncoding();
			charset = (charsetStr == null) ? null : Charset.forName(charsetStr);
		} else {
			charset = null;
		}
	}

	/**
	 * Creates a folded line reader.
	 * @param text the text to read
	 */
	public FoldedLineReader(String text) {
		this(new StringReader(text));
	}

	/**
	 * Sets whether the reader will only ignore the first whitespace character
	 * it encounters at the beginning of a folded line (as per the
	 * specifications). This setting is enabled by default.
	 * @param enabled true to enable, false to disable
	 */
	public void setSingleSpaceFoldingEnabled(boolean enabled) {
		singleSpaceFolding = enabled;
	}

	/**
	 * Gets whether the reader will only ignore the first whitespace character
	 * it encounters at the beginning of a folded line (as per the
	 * specifications). This setting is enabled by default.
	 * @return true if enabled, false if disabled
	 */
	public boolean isSingleSpaceFoldingEnabled() {
		return singleSpaceFolding;
	}

	/**
	 * Gets the starting line number of the last unfolded line that was read.
	 * @return the line number
	 */
	public int getLineNum() {
		return lastLineNum;
	}

	/**
	 * Gets the character encoding of the reader.
	 * @return the character encoding or null if none is defined
	 */
	public Charset getEncoding() {
		return charset;
	}

	/**
	 * Reads the next non-empty line.
	 * @return the next non-empty line or null of EOF
	 * @throws IOException if there's a problem reading from the reader
	 */
	private String readNonEmptyLine() throws IOException {
		while (true) {
			String line = super.readLine();
			if (line == null) {
				return null;
			}

			lineCount++;
			if (line.length() > 0) {
				return line;
			}
		}
	}

	/**
	 * Reads the next unfolded line.
	 * @return the next unfolded line or null if the end of the stream has been
	 * reached
	 * @throws IOException if there's a problem reading from the reader
	 */
	@Override
	public String readLine() throws IOException {
		String wholeLine = (lastLine == null) ? readNonEmptyLine() : lastLine;
		lastLine = null;
		if (wholeLine == null) {
			//end of stream
			return null;
		}

		//@formatter:off
		/*
		 * Lines that are QUOTED-PRINTABLE are folded in a strange way. A "=" is
		 * appended to the end of a line to signal that the next line is folded.
		 * Also, each folded line is *not* prepend with whitespace (which it should
		 * be, according to the 2.1 specs).
		 * 
		 * For example:
		 * 
		 * ------------
		 * BEGIN:VCARD
		 * NOTE;QUOTED-PRINTABLE: This is an=0D=0A=
		 * annoyingly formatted=0D=0A=
		 * note=
		 * 
		 * END:VCARD
		 * ------------
		 * 
		 * In the example above, note how there is an empty line directly above
		 * END. This is still part of the NOTE property value because the 3rd
		 * line of NOTE ends with a "=".
		 * 
		 * This behavior has only been observed in Outlook vCards. >:(
		 */
		//@formatter:on

		boolean foldedQuotedPrintableLine = foldedQuotedPrintableValueRegex.matcher(wholeLine).matches();
		if (foldedQuotedPrintableLine) {
			//chop off the trailing "="
			wholeLine = chop(wholeLine);
		}

		lastLineNum = lineCount;
		StringBuilder unfoldedLine = new StringBuilder(wholeLine);
		while (true) {
			String line = foldedQuotedPrintableLine ? super.readLine() : readNonEmptyLine();
			if (line == null) {
				//end of stream
				break;
			}

			if (foldedQuotedPrintableLine) {
				if (isFoldedLine(line)) {
					line = removeFoldingWhitespace(line);
				}

				boolean endsInEquals = line.endsWith("=");
				if (endsInEquals) {
					//chop off the trailing "="
					line = chop(line);
				}

				unfoldedLine.append(line);

				if (endsInEquals) {
					//there are more folded lines
					continue;
				}

				//end of the folded line
				break;
			}

			if (isFoldedLine(line)) {
				line = removeFoldingWhitespace(line);
				unfoldedLine.append(line);
				continue;
			}

			lastLine = line;
			break;
		}

		return unfoldedLine.toString();
	}

	/**
	 * Determines if a line is folded or not.
	 * @param line the line
	 * @return true if it's folded, false if not
	 */
	private boolean isFoldedLine(String line) {
		if (line.length() == 0) {
			return false;
		}

		char first = line.charAt(0);
		return isWhitespace(first);
	}

	/**
	 * Removes folding whitespace from a line.
	 * @param line the folded line (assumes the line begins with whitespace)
	 * @return the line with the folding whitespace removed
	 */
	private String removeFoldingWhitespace(String line) {
		if (singleSpaceFolding) {
			return line.substring(1);
		}

		int index = 1;
		while (index < line.length() && isWhitespace(line.charAt(index))) {
			index++;
		}
		return line.substring(index);
	}

	/**
	 * Determines if a character is considered whitespace.
	 * @param c the character
	 * @return true if the character is whitespace, false if not
	 */
	private boolean isWhitespace(char c) {
		return c == ' ' || c == '\t';
	}

	/**
	 * Removes the last character from a string.
	 * @param string the string
	 * @return the modified string
	 */
	private static String chop(String string) {
		return (string.length() > 0) ? string.substring(0, string.length() - 1) : string;
	}
}
