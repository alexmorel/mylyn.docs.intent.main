/*******************************************************************************
 * Copyright (c) 2010, 2011 Obeo.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.mylyn.docs.intent.parser.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.mylyn.docs.intent.parser.IntentKeyWords;
import org.eclipse.mylyn.docs.intent.parser.IntentParserUtil;
import org.eclipse.mylyn.docs.intent.parser.modelingunit.ModelingUnitParser;
import org.eclipse.mylyn.docs.intent.parser.modelingunit.ParseException;

/**
 * Parser which parse any IntentStructuredElement and its content.
 * 
 * @author <a href="mailto:alex.lagarde@obeo.fr">Alex Lagarde</a>
 */
public class IntentDocumentParser {

	/**
	 * The builder used for generating the parsed elements.
	 */
	private IntentBuilder builder;

	private int offSet;

	/**
	 * Indicates if the given textual form describing a IntentStructuredElement.
	 * 
	 * @param contentToParse
	 *            the given textual to study
	 * @return true if the given textual form describing a IntentStructuredElement, false otherwise
	 */
	public boolean isParserFor(String contentToParse) {
		// This textual form describes a IntentStructured Element if it starts with the correct keyword.
		// The valid combinations are :
		// "[internal|hidden]? Chapter"
		// "[internal|hidden]? Section"
		String[] validFirstKeyWords = getValidFirstKeyWords();
		int validFirstKeyWordIndice = 0;
		boolean isValidContent = false;
		while (validFirstKeyWordIndice < validFirstKeyWords.length && !isValidContent) {
			// We simply test, for any valid combination, if the given content match one of them
			isValidContent = contentToParse.startsWith(validFirstKeyWords[validFirstKeyWordIndice]);
			validFirstKeyWordIndice++;
		}
		return isValidContent;
	}

	/**
	 * Parse the given textual form of a IntentStructuredElement.
	 * 
	 * @param contentToParse
	 *            the contentToParse
	 * @return the IntentStructuredElement described by the given content to parse
	 * @throws ParseException
	 *             if the given content to parse contains errors
	 */
	public EObject parse(String contentToParse) throws ParseException {
		// Step 1 : creating the builder which will generate the elements
		builder = new IntentBuilder();
		String remainingContentToParse = contentToParse;
		String currentlyParsedSentence = null;
		offSet = 0;

		// Step 2 : parsing
		// while there is still content to parse
		while ((remainingContentToParse.length() > 0) && (offSet != -1)) {

			// Step 2.1 : We get the offset of the next breaking flow token (for example "}")
			offSet = IntentParserUtil.getNextOffset(remainingContentToParse);
			// If any offset found
			if (offSet != -1) {
				// We get the sentence to parse (i.e the remaining content to parse from
				// the beginning to the calculated offset
				currentlyParsedSentence = remainingContentToParse.substring(0, offSet);
				// Step 2.2 : We send the appopriate signal to the builder
				sendSignal(currentlyParsedSentence);

				// Step 2.3 (optional - if the current parsedSentence is a ModelingUnit declaration)
				if (currentlyParsedSentence.contains(ModelingUnitParser.MODELING_UNIT_PREFIX)) {
					// We parse until we find the suffix of this Modeling Unit
					int endModelingUnitOffSet = remainingContentToParse
							.indexOf(ModelingUnitParser.MODELING_UNIT_SUFFIX);
					if (offSet < 0 || endModelingUnitOffSet < 0) {
						throw new ParseException("Unclosed Modeling Unit", offSet,
								ModelingUnitParser.MODELING_UNIT_SUFFIX.length());
					}
					endModelingUnitOffSet = endModelingUnitOffSet
							+ ModelingUnitParser.MODELING_UNIT_SUFFIX.length();
					String modelingUnitContent = remainingContentToParse.substring(offSet,
							endModelingUnitOffSet);
					// We send the appropriate signal to the builder
					sendModelingUnitSignal(modelingUnitContent);
					offSet = endModelingUnitOffSet;
				}
				// Step 2.4 : We update the remaining content to parse by removing the parsed sentence
				remainingContentToParse = remainingContentToParse.substring(offSet);
			}
		}

		// Step 3 : we finally return the root generated by the builder
		// if there are multiple roots, throw an exception because only one root should be described
		return builder.getRoot();
	}

	/**
	 * Sends the correct signal(s) to the builder according to the given parsedSentence value.
	 * 
	 * @param parsedSentence
	 *            the String corresponding to the signal to send
	 * @throws ParseException
	 *             if the sent signal contains any error
	 */
	private void sendSignal(String parsedSentence) throws ParseException {
		// First, we determine if the new Keywords ends a description Unit
		// If so, we send the corresponding signal to the builder
		String parsedSentenceWithoutDescriptionUnit = sendDescriptionUnitSignalIfNecessary(parsedSentence);

		if (parsedSentenceWithoutDescriptionUnit.contains(IntentKeyWords.INTENT_KEYWORD_CLOSE)) {
			builder.endStructuredElement();
		}
		// If the parsed Sentence describes the begining of a session
		if (parsedSentenceWithoutDescriptionUnit.contains(IntentKeyWords.INTENT_KEYWORD_DOCUMENT)) {
			builder.beginDocument();
		}
		// If the parsed Sentence describes the begining of a session
		if (parsedSentenceWithoutDescriptionUnit.contains(IntentKeyWords.INTENT_KEYWORD_CHAPTER)) {
			builder.beginChapter();
		}
		if (parsedSentenceWithoutDescriptionUnit.contains(IntentKeyWords.INTENT_KEYWORD_SECTION)) {
			sendBeginSectionSignal(parsedSentenceWithoutDescriptionUnit);
		}

	}

	/**
	 * If the parsed Sentence matches a description Unit, sends the corresponding signal to the builder.
	 * 
	 * @param parsedSentence
	 *            the sentence to analyze.
	 * @return the parsedSentence from witch we remove the description unit
	 * @throws ParseException
	 *             if the sent description unit contains any parse error.
	 */
	private String sendDescriptionUnitSignalIfNecessary(String parsedSentence) throws ParseException {
		String descriptionUnitContent = parsedSentence;
		String parsedSentenceWithoutDescriptionUnit = parsedSentence;

		// For each keyWord that can end a description unit
		for (String endingDescriptionUnitKeyword : IntentParserUtil.getEndingDescriptionUnitTokens()) {

			Pattern ptr = Pattern.compile(endingDescriptionUnitKeyword);
			Matcher matcher = ptr.matcher(descriptionUnitContent);
			// If the parsed Sentence contains this keyWord (i.e. ends a description unit), we remove it
			if (matcher.find()) {
				descriptionUnitContent = descriptionUnitContent.substring(0, matcher.start()).trim();
			}
		}

		if (descriptionUnitContent.trim().length() > 0) {

			parsedSentenceWithoutDescriptionUnit = parsedSentenceWithoutDescriptionUnit.replace(
					descriptionUnitContent, "");

			builder.descriptionUnitContent(descriptionUnitContent);
		}

		return parsedSentenceWithoutDescriptionUnit;
	}

	/**
	 * Sends a Modeling Content signal, with the given content of the modeling Unit.
	 * 
	 * @param modelingUnitContent
	 *            the textual content of the modeling unit
	 * @throws ParseException
	 *             if the given modeling unit content contain errors
	 */
	private void sendModelingUnitSignal(String modelingUnitContent) throws ParseException {
		// We first remove the non usefull indentation (not correctly parsed by Xtext)
		String modelingUnitContentReFormatted = modelingUnitContent;

		builder.modelingUnitContent(ModelingUnitParser.MODELING_UNIT_PREFIX
				+ IntentKeyWords.INTENT_WHITESPACE + modelingUnitContentReFormatted);

	}

	/**
	 * Sends a beginSection signal, followed by a sectionOption signal according to the given parsed Sentence.
	 * 
	 * @param parsedSentence
	 *            the parsed String
	 */
	private void sendBeginSectionSignal(String parsedSentence) {
		String parsedSentenceCopy = parsedSentence;
		String visibility = null;

		// Visibility consideration
		if (!(parsedSentence.startsWith(IntentKeyWords.INTENT_KEYWORD_SECTION))) {
			visibility = parsedSentence.substring(0,
					parsedSentence.indexOf(IntentKeyWords.INTENT_KEYWORD_SECTION)).trim();
			parsedSentenceCopy = parsedSentenceCopy.replace(visibility, "").trim();

			if (visibility.length() == 0) {
				visibility = null;
			}
		}
		// Headers considerations
		List<String> headerReferences = new ArrayList<String>();
		parsedSentenceCopy = parsedSentenceCopy.replace(IntentKeyWords.INTENT_KEYWORD_SECTION, "").trim();
		if (parsedSentenceCopy.contains(IntentKeyWords.INTENT_KEYWORD_HEADER_REFERENCE_OPEN)) {
			parsedSentenceCopy = parsedSentenceCopy
					.replace(IntentKeyWords.INTENT_KEYWORD_HEADER_REFERENCE_OPEN, "")
					.replace(IntentKeyWords.INTENT_KEYWORD_HEADER_CLOSE, "")
					.replace(IntentKeyWords.INTENT_KEYWORD_OPEN, "");

			String[] headersTable = parsedSentenceCopy
					.split(IntentKeyWords.INTENT_KEYWORD_HEADER_REFERENCE_SEPARATOR);
			for (int i = 0; i < headersTable.length; i++) {
				headerReferences.add(headersTable[i].trim());
			}
		}

		// Sending the signal : we first indicate the beginning of a section
		builder.beginSection();
		// then we send the options associated to this section
		builder.sectionOptions(visibility, headerReferences);
	}

	/**
	 * Returns all the valid combinations that can represent the first key word of a IntentDocument.
	 * 
	 * @return all the valid combinations that can represent the first key word of a IntentDocument
	 */
	private String[] getValidFirstKeyWords() {
		// The valid combinations are :
		// Document
		// "[internal|hidden]? Chapter"
		// "[internal|hidden]? Section"
		final int numberOfValidKeyWords = 7;
		String[] validKeyWords = new String[numberOfValidKeyWords];
		validKeyWords[0] = IntentKeyWords.INTENT_KEYWORD_CHAPTER;
		validKeyWords[1] = IntentKeyWords.INTENT_KEYWORD_VISIBILITY_INTERNAL
				+ IntentKeyWords.INTENT_WHITESPACE + IntentKeyWords.INTENT_KEYWORD_CHAPTER;
		validKeyWords[2] = IntentKeyWords.INTENT_KEYWORD_VISIBILITY_HIDDEN + IntentKeyWords.INTENT_WHITESPACE
				+ IntentKeyWords.INTENT_KEYWORD_CHAPTER;
		validKeyWords[3] = IntentKeyWords.INTENT_KEYWORD_SECTION;
		validKeyWords[4] = IntentKeyWords.INTENT_KEYWORD_VISIBILITY_HIDDEN + IntentKeyWords.INTENT_WHITESPACE
				+ IntentKeyWords.INTENT_KEYWORD_SECTION;
		validKeyWords[5] = IntentKeyWords.INTENT_KEYWORD_VISIBILITY_INTERNAL
				+ IntentKeyWords.INTENT_WHITESPACE + IntentKeyWords.INTENT_KEYWORD_SECTION;
		validKeyWords[6] = IntentKeyWords.INTENT_KEYWORD_DOCUMENT;
		return validKeyWords;
	}
}