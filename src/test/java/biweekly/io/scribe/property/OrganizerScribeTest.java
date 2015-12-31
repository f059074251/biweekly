package biweekly.io.scribe.property;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import biweekly.io.ParseContext;
import biweekly.io.scribe.property.Sensei.Check;
import biweekly.property.Organizer;

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
 * @author Michael Angstadt
 */
public class OrganizerScribeTest extends ScribeTest<Organizer> {
	private final String name = "John Doe";
	private final String email = "jdoe@example.com";
	private final String uri = "http://example.com/jdoe";

	private final Organizer withEmail = new Organizer(null, email);
	private final Organizer withNameEmail = new Organizer(name, email);
	private final Organizer withNameEmailUri = new Organizer(name, email);
	{
		withNameEmailUri.setUri(uri);
	}

	public OrganizerScribeTest() {
		super(new OrganizerScribe());
	}

	@Test
	public void prepareParameters() {
		sensei.assertPrepareParams(withEmail).run();
		sensei.assertPrepareParams(withNameEmail).expected("CN", name).run();
		sensei.assertPrepareParams(withNameEmailUri).expected("CN", name).run();
	}

	@Test
	public void writeText() {
		sensei.assertWriteText(withEmail).run("mailto:" + email);
		sensei.assertWriteText(withNameEmail).run("mailto:" + email);
		sensei.assertWriteText(withNameEmailUri).run(uri);
	}

	@Test
	public void parseText() {
		sensei.assertParseText("mailto:" + email).run(check(null, email, null));
		sensei.assertParseText("mailto:" + email).param("CN", name).run(check(name, email, null));
		sensei.assertParseText("MAILTO:" + email).run(check(null, email, null));
		sensei.assertParseText("MAILTO:" + email).param("CN", name).run(check(name, email, null));
		sensei.assertParseText(uri).run(check(null, null, uri));
	}

	private Check<Organizer> check(final String name, final String email, final String uri) {
		return new Check<Organizer>() {
			public void check(Organizer property, ParseContext context) {
				assertTrue(property.getParameters().isEmpty());
				assertEquals(name, property.getCommonName());
				assertEquals(email, property.getEmail());
				assertEquals(uri, property.getUri());
			}
		};
	}
}
