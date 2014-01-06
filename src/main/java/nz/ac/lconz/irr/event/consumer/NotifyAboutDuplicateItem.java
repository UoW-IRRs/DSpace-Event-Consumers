package nz.ac.lconz.irr.event.consumer;

import org.dspace.content.DCValue;
import org.dspace.content.Item;
import org.dspace.core.*;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.event.Consumer;
import org.dspace.event.Event;
import org.dspace.handle.HandleManager;

import javax.mail.MessagingException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Locale;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz for the LCoNZ Institutional Research Repositories
 *
 * Event consumer to notify a given group when an item is made live that contains "this item replaces another item with handle" in dc.description.provenance.
 */
public class NotifyAboutDuplicateItem implements Consumer {
	private Integer recipientsGroupId = -1;
	private String triggerString = "this item replaces another item with handle";

	public void initialize() throws Exception {
		String recipientsGroup = ConfigurationManager.getProperty("lconz-event", "notify.duplicate.recipients-group-id");
		if (recipientsGroup == null || recipientsGroup.length() == 0) {
			System.err.println("NotifyAboutDuplicateItem: no configuration value found for notification recipients, can't initialise.");
			return;
		}
		try {
			recipientsGroupId = Integer.valueOf(recipientsGroup);
		} catch (NumberFormatException e) {
			System.err.printf("NotifyAboutDuplicateItem: notification recipients group ID %s isn't numeric, can't initialise.\n", recipientsGroup);
			return;
		}
	}

	public void consume(Context context, Event event) throws Exception {
		if (event.getSubjectType() != Constants.ITEM || event.getEventType() != Event.INSTALL) {
			return; // wrong type of dso or of event -> ignore
		}

		Item item = (Item) event.getSubject(context);
		DCValue[] provenanceValues = item.getMetadata("dc.description.provenance");
		for (DCValue provenanceValue : provenanceValues) {
			if (provenanceValue != null && provenanceValue.value != null && provenanceValue.value.contains(triggerString)) {
				sendNotification(context, item);
			}
		}
	}

	private void sendNotification(Context context, Item item) throws SQLException, IOException, MessagingException {
		Group recipients = Group.find(context, recipientsGroupId);
		if (recipients == null) {
			System.err.println("NotifyAboutDuplicateItem: notification group not found");
			return;
		}

		String emailFilename = I18nUtil.getEmailFilename(Locale.getDefault(), "notify_duplicate");
		if (emailFilename == null || "".equals(emailFilename.trim())) {
			System.err.println("NotifyAboutDuplicateItem: e-mail template (notify_duplicate) not found");
			return; // no e-mail to send
		}
		Email message = Email.getEmail(emailFilename);
		for (EPerson member : recipients.getMembers()) {
			message.addRecipient(member.getEmail());
		}
		message.addArgument(HandleManager.resolveToURL(context, item.getHandle()));
		message.send();
	}

	public void end(Context context) throws Exception {
	}

	public void finish(Context context) throws Exception {
	}
}
