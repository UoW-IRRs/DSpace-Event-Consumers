package nz.ac.lconz.irr.event.consumer;

import org.apache.commons.lang.StringUtils;
import org.dspace.content.DCDate;
import org.dspace.content.Item;
import org.dspace.content.Metadatum;
import org.dspace.core.*;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
import org.dspace.handle.HandleManager;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz for the LCoNZ IRRs
 *         <p/>
 *         DSpace event consumer that sends an e-mail to the thesis author when an item is archived.
 *         <p/>
 *         This is useful for theses deposited via Sword since the submitter is set in DSpace to an e-mail address
 *         for the sword form rather than that of the actual author of the item.
 *         <p/>
 *         The metadata field for the thesis author is expected to be set in a DSpace configuration variable with the key
 *         <code>notify.author.archive.field</code>.
 *         <p/>
 *         The template for the e-mail is expected to be called <i>author_notify_archive</i>
 *         in the dspace/config/emails directory.
 *         <p/>
 *         Additional functionality is available for items with a specific, configurable field present:
 *         when <code>notify.author.archive.special.field</code> is set and the item has a non-empty metadata value for
 *         this metadata field, two additional arguments are made available to the e-mail template that consist of
 *         a special message as well as the value of the field, optionally (if <code>notify.author.archive.special.type</code>
 *         is set to a value of <code>date</code>) formatted as a user-friendly date string. The
 *         message needs to be configured in <code>[dspace-src]/dspace-api/src/main/resources/Messages.properties</code>.
 *         If the special field is not present, the two additional arguments will be the empty string.
 */
public class NotifyRealAuthorOfArchive implements Consumer {
	private String schema;
	private String element;
	private String qualifier;
	private String specialField;
	private String specialFieldType;

	public void initialize() throws Exception {
		String emailField = ConfigurationManager.getProperty("lconz-event", "notify.author.archive.field");
		if (emailField == null || emailField.length() == 0) {
			System.err.println("NotifyRealAuthorOfArchive: no configuration value found for author e-mail field, can't initialise.");
			return;
		}
		String[] components = emailField.split("\\.");
		if (components.length < 2) {
			System.err.println("NotifyRealAuthorOfArchive: no configuration value found for author e-mail field, can't initialise.");
			return;
		}

		schema = components[0];
		element = components[1];
		if (components.length > 2) {
			qualifier = components[2];
		}

		specialField = ConfigurationManager.getProperty("lconz-event", "notify.author.archive.special.field");
		specialFieldType = ConfigurationManager.getProperty("lconz-event", "notify.author.archive.special.type");
	}

	public void consume(Context context, Event event) throws Exception {
		if (schema == null && element == null && qualifier == null) {
			System.err.println("NotifyRealAuthorOfArchive: no configuration value found for author e-mail field, aborting.");
			return;
		}

		if (event.getSubjectType() != Constants.ITEM || event.getEventType() != Event.INSTALL) {
			return; // wrong type of dso or of event -> ignore
		}
		Item item = (Item) event.getSubject(context);
		String submitterEmail = item.getSubmitter().getEmail();
		Metadatum[] authors = item.getMetadata(schema, element, qualifier, Item.ANY);
		if (authors == null || authors.length == 0) {
			return; // nothing to do
		}

		String emailFilename = I18nUtil.getEmailFilename(Locale.getDefault(), "author_notify_archive");
		if (emailFilename == null || "".equals(emailFilename.trim())) {
			return; // no e-mail to send
		}
		Email message = Email.getEmail(emailFilename);
		int recipients = 0;
		for (Metadatum author : authors) {
			String authorEmail = author.value;
			if (authorEmail != null && !authorEmail.equalsIgnoreCase(submitterEmail)) {
				message.addRecipient(authorEmail);
				recipients++;
			}
		}
		if (recipients == 0) {
			return; // no e-mail to send
		}

		message.addArgument(item.getName());
		message.addArgument(item.getOwningCollection().getName());
		message.addArgument(HandleManager.getCanonicalForm(item.getHandle()));

		boolean addedSpecialText = false;
		if (StringUtils.isNotBlank(specialField)) {
			Metadatum[] values = item.getMetadataByMetadataString(specialField);
			if (values != null && values.length > 0 && values[0] != null && StringUtils.isNotBlank(values[0].value)) {
				message.addArgument(I18nUtil.getMessage("lconz-extra.notify-author.special.message"));
				String formattedValue = formatValue(values[0].value, specialFieldType);
				message.addArgument(formattedValue);
				addedSpecialText = true;
			}
		}
		if (!addedSpecialText) {
			// blank out values to prevent placeholder from showing up in e-mail
			message.addArgument("");
			message.addArgument("");
		}

		message.send();
	}

	private String formatValue(String specialValue, String specialFieldType) {
		if ("date".equals(specialFieldType)) {
			DCDate date = new DCDate(specialValue);
			return SimpleDateFormat.getDateInstance(DateFormat.MEDIUM).format(date.toDate());
		} else {
			return specialValue;
		}
	}

	public void end(Context context) throws Exception {
	}

	public void finish(Context context) throws Exception {
	}
}
