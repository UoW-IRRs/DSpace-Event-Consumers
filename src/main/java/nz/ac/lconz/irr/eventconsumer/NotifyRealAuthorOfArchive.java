package nz.ac.lconz.irr.eventconsumer;

import org.dspace.content.DCValue;
import org.dspace.content.Item;
import org.dspace.core.*;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
import org.dspace.handle.HandleManager;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Locale;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz
 *
 * DSpace event consumer that sends an e-mail to the thesis author when an item is archived.
 *
 * This is useful for theses deposited via Sword since the submitter is set in DSpace to an e-mail address
 * for the sword form rather than that of the actual author of the item.
 *
 * The metadata field for the thesis author is expected to be set in a DSpace configuration variable with the key
 * <code>notify.author.archive.field</code>.
 *
 * The template for the e-mail is expected to be called <i>author_notify_archive</i>
 * in the dspace/config/emails directory.
 *
 */
public class NotifyRealAuthorOfArchive implements Consumer {
	private String schema;
	private String element;
	private String qualifier;

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
		DCValue[] authors = item.getMetadata(schema, element, qualifier, Item.ANY);
		if (authors == null || authors.length == 0) {
			return; // nothing to do
		}

		Email message = ConfigurationManager.getEmail(I18nUtil.getEmailFilename(Locale.getDefault(), "author_notify_archive"));
		int recipients = 0;
		for (DCValue author : authors) {
			String authorEmail = author.value;
			if (authorEmail != null && ! authorEmail.equalsIgnoreCase(submitterEmail)) {
				message.addRecipient(authorEmail);
				recipients++;
			}
		}
		item.decache();
		if (recipients == 0) {
			return; // no e-mail to send
		}

		message.addArgument(item.getName());
		message.addArgument(item.getOwningCollection().getName());
		message.addArgument(HandleManager.getCanonicalForm(item.getHandle()));

		message.send();
	}

	public void end(Context context) throws Exception {
	}

	public void finish(Context context) throws Exception {
	}
}
