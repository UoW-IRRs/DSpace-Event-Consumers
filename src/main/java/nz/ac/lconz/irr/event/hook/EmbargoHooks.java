package nz.ac.lconz.irr.event.hook;

import nz.ac.lconz.irr.event.util.CurationHelper;
import org.apache.log4j.Logger;
import org.dspace.content.DCDate;
import org.dspace.content.DCValue;
import org.dspace.content.Item;
import org.dspace.content.MetadataSchema;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.embargo.EmbargoManager;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.handle.HandleManager;

import javax.mail.MessagingException;
import java.io.IOException;
import java.sql.SQLException;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz
 *         <p/>
 *         Configuration properties:
 *         <p/>
 *         # group id of the group who should receive notification e-mails
 *         # related to endorsed restrictions
 *         embargo.notify.groupid = 53
 */
@SuppressWarnings({"deprecation"})
public class EmbargoHooks {
	private static final Logger log = Logger.getLogger(EmbargoHooks.class);

	// template for notification e-mail (embargo lifted)
	private static final String EMAIL_TEMPLATE_NOTIFY_LIFTED = "notify_embargo_lift";
	// template for notification e-mail (embargo set)
	private static final String EMAIL_TEMPLATE_NOTIFY_SET = "notify_embargo_set";
	// template for notification e-mail (embargo expired)
	private static final String EMAIL_TEMPLATE_NOTIFY_EXPIRED = "notify_embargo";
	// template for notification e-mail (embargo about to expire)
	private static final String EMAIL_TEMPLATE_NOTIFY_ADVANCE_EXPIRY = "notify_embargo_advance";
	// template for notification e-mail (permissions of embargoed item incorrect)
	private static final String EMAIL_TEMPLATE_NOTIFY_PERMISSIONS = "notify_embargo_permissions";
	// group id for group that receives notification e-mails
	private static final String EMBARGO_NOTIFICATION_GROUP_ID = "embargo.notify.groupid";

	private static final String EMBARGO_SET_CURATION_QUEUE_NAME = "queue.embargo.set.name";
	private static final String EMBARGO_LIFTED_CURATION_QUEUE_NAME = "queue.embargo.lifted.name";
	private static final String EMBARGO_SET_CURATION_TASKS = "queue.embargo.set.tasks";
	private static final String EMBARGO_LIFTED_CURATION_TASKS = "queue.embargo.lifted.tasks";
	
	/**
	 * Hooks to be run when an item has an embargo set *
	 */
	public static void atEmbargoSet(Context context, Item item, DCDate expiryDate) {
		log.info("Embargo set on item " + item.getID() + ", expires " + expiryDate.displayLocalDate(false, context.getCurrentLocale()));
		notifyEmbargoSet(context, item, expiryDate);
		try {
			queueForCuration(context, item, EMBARGO_SET_CURATION_TASKS, EMBARGO_SET_CURATION_QUEUE_NAME);
		} catch (IOException e) {
			log.warn("Caught exception while trying to queue curation task when embargo was set on item id=" + item.getID());
		}
	}

	public static void atEmbargoLifted(Context context, Item item) {
		log.info("Embargo lifted on item " + item.getID());
		notifyEmbargoLifted(context, item);
		try {
			queueForCuration(context, item, EMBARGO_LIFTED_CURATION_TASKS, EMBARGO_LIFTED_CURATION_QUEUE_NAME);
		} catch (IOException e) {
			log.warn("Caught exception while trying to queue curation task when embargo was lifted on item id=" + item.getID());
		}
	}

	private static void queueForCuration(Context context, Item item, String tasksProperty, String queueProperty) throws IOException {
		CurationHelper helper = new CurationHelper();
		helper.initTaskNames(tasksProperty);
		if (!helper.hasTaskNames()) {
			return; // nothing to do
		}
		helper.initQueueName(queueProperty);
		helper.addToQueue(item);
		helper.queueForCuration(context);
	}

	public static void atEmbargoExpired(Context context, Item item, DCDate liftDate) {
		notifyEmbargoExpired(context, item, liftDate);
	}

	public static void atEmbargoAboutToExpire(Context context, Item item, DCDate liftDate) {
		notifyEmbargoAboutToExpire(context, item, liftDate);
	}

	public static void atPermissionsIncorrect(Context context, Item item) {
		try {
			notifyPermissionsIncorrect(context, item);
		} catch (SQLException e) {
			log.warn("Cannot notify about incorrect permissions on item id=" + item.getID(), e);
		}
	}

	private static void notifyEmbargoSet(Context context, Item item, DCDate liftDate) {
		// find out who requested this action
		String name = "Unknown User";
		String email = "probably automated";

		// populate with real data if there is a current user
		EPerson p = context.getCurrentUser();
		if (p != null) {
			email = p.getEmail();
			name = p.getFullName();
		}

		// add a provenance message
		StringBuilder provmessage = new StringBuilder("Endorsed restriction until ");
		provmessage.append(liftDate);
		provmessage.append(" set by ");
		provmessage.append(name);
		provmessage.append(" (");
		provmessage.append(email);
		provmessage.append(") ");
		provmessage.append("on ");
		provmessage.append(DCDate.getCurrent());
		provmessage.append(" (UTC)\n");
		item.addMetadata(MetadataSchema.DC_SCHEMA, "description", "provenance", "en_NZ", provmessage.toString());

		// notify thesis administrators
		EPerson[] recipients;
		try {
			recipients = findNotificationRecipients(context);
		} catch (SQLException sqle) {
			log.warn("Can't notify thesis admins of lifted embargo.", sqle);
			return;
		}
		// Send email to thesis administrators

		try {
			// Get some basic metadata
			DCValue titles[] = item.getMetadata(MetadataSchema.DC_SCHEMA, "title", null, Item.ANY);
			DCValue authors[] = item.getMetadata(MetadataSchema.DC_SCHEMA, "contributor", "author", Item.ANY);

			String title = titles.length > 0 ? titles[0].value : "no title";
			String author = authors.length > 0 ? authors[0].value : "no authors";

			// Send email
			Email emailmsg = ConfigurationManager.getEmail(I18nUtil.getEmailFilename(context.getCurrentLocale(), EMAIL_TEMPLATE_NOTIFY_SET));
			for (EPerson recipient : recipients) {
				emailmsg.addRecipient(recipient.getEmail());
			}
			String itemIdentifier = "[workflow item]";
			String url = "the item's workflow screen";
			if (item.getHandle() != null) {
				itemIdentifier = item.getHandle();
				try {
					String link = HandleManager.resolveToURL(context, itemIdentifier);
					if (link != null)
						url = link;
				} catch (SQLException ex) {
					log.warn("can't determine url to item from handle " + itemIdentifier, ex);
				}
			}
			emailmsg.addArgument(itemIdentifier);
			emailmsg.addArgument(title);
			emailmsg.addArgument(author);
			emailmsg.addArgument(name);
			emailmsg.addArgument(email);
			emailmsg.addArgument(liftDate.toString());
			emailmsg.addArgument(url);
			emailmsg.send();
		} catch (IOException ioe) {
			log.warn("Problem sending notification email when setting embargo", ioe);
		} catch (MessagingException me) {
			log.warn("Problem sending notification email when setting embargo", me);
		}
	}

	private static void notifyEmbargoLifted(Context context, Item item) {
		// find out who requested this action
		String name = "Unknown User";
		String email = "probably automated";

		// populate with real data if there is a current user
		EPerson p = context.getCurrentUser();
		if (p != null) {
			email = p.getEmail();
			name = p.getFullName();
		}

		// add a provenance message
		StringBuilder provmessage = new StringBuilder("Endorsed restriction lifted by ");
		provmessage.append(name);
		provmessage.append(" (");
		provmessage.append(email);
		provmessage.append("on ");
		provmessage.append(DCDate.getCurrent());
		provmessage.append(" (UTC)\n");
		item.addMetadata(MetadataSchema.DC_SCHEMA, "description", "provenance", "en_NZ", provmessage.toString());

		// notify the thesis admins
		EPerson[] recipients;
		try {
			recipients = findNotificationRecipients(context);
		} catch (SQLException sqle) {
			log.warn("Can't notify thesis admins of lifted embargo.", sqle);
			return;
		}
		// Send email to thesis administrators
		try {
			// Get some basic metadata
			DCValue[] titles = item.getMetadata(MetadataSchema.DC_SCHEMA, "title", null, Item.ANY);
			DCValue[] authors = item.getMetadata(MetadataSchema.DC_SCHEMA, "contributor", "author", Item.ANY);
			String title = titles.length > 0 ? titles[0].value : "no title";
			String author = authors.length > 0 ? authors[0].value : "no authors";
			// Send email
			Email emailmsg = ConfigurationManager.getEmail(I18nUtil.getEmailFilename(context.getCurrentLocale(), EMAIL_TEMPLATE_NOTIFY_LIFTED));
			for (EPerson recipient : recipients) {
				emailmsg.addRecipient(recipient.getEmail());
			}
			String itemIdentifier = "[workflow item]";
			String url = "the item's workflow screen";
			if (item.getHandle() != null) {
				itemIdentifier = item.getHandle();
				try {
					String link = HandleManager.resolveToURL(context, itemIdentifier);
					if (link != null)
						url = link;
				} catch (SQLException ex) {
					log.warn("can't determine url to item from handle " + itemIdentifier, ex);
				}
			}
			emailmsg.addArgument(itemIdentifier);
			emailmsg.addArgument(title);
			emailmsg.addArgument(author);
			emailmsg.addArgument(name);
			emailmsg.addArgument(email);
			emailmsg.addArgument(url);
			emailmsg.send();
		} catch (IOException ioe) {
			log.warn("Problem sending notification email when lifting embargo", ioe);
		} catch (MessagingException me) {
			log.warn("Problem sending notification email when lifting embargo", me);
		}
	}

	private static void notifyEmbargoExpired(Context context, Item item, DCDate liftDate) {
		// notify the thesis admins
		EPerson[] recipients;
		try {
			recipients = findNotificationRecipients(context);
		} catch (SQLException sqle) {
			log.warn("Can't notify thesis admins of lifted embargo.", sqle);
			return;
		}
		// Send email to thesis administrators
		try {
			// Get some basic metadata
			DCValue[] titles = item.getMetadata(MetadataSchema.DC_SCHEMA, "title", null, Item.ANY);
			DCValue[] authors = item.getMetadata(MetadataSchema.DC_SCHEMA, "contributor", "author", Item.ANY);
			String title = titles.length > 0 ? titles[0].value : "no title";
			String author = authors.length > 0 ? authors[0].value : "no authors";
			// Send email
			Email emailmsg = ConfigurationManager.getEmail(I18nUtil.getEmailFilename(context.getCurrentLocale(), EMAIL_TEMPLATE_NOTIFY_EXPIRED));
			for (EPerson recipient : recipients) {
				emailmsg.addRecipient(recipient.getEmail());
			}

			String itemIdentifier = "[workflow item]";
			String url = "the item's workflow screen";
			if (item.getHandle() != null) {
				itemIdentifier = item.getHandle();
				try {
					String link = HandleManager.resolveToURL(context, itemIdentifier);
					if (link != null)
						url = link;
				} catch (SQLException ex) {
					log.warn("can't determine url to item from handle " + itemIdentifier, ex);
				}
			}
			String collectionName = item.getOwningCollection().getName();

			emailmsg.addArgument(itemIdentifier);
			emailmsg.addArgument(title);
			emailmsg.addArgument(author);
			emailmsg.addArgument(liftDate.toString());
			emailmsg.addArgument(url);
			emailmsg.addArgument(collectionName);
			emailmsg.send();
		} catch (IOException ioe) {
			log.warn("Problem sending notification email when detecting expired embargo", ioe);
		} catch (MessagingException me) {
			log.warn("Problem sending notification email when detecting expired embargo", me);
		} catch (SQLException e) {
			log.warn("Problem sending notification email when detecting expired embargo", e);
		}
	}

	private static void notifyEmbargoAboutToExpire(Context context, Item item, DCDate liftDate) {
		// notify the thesis admins
		EPerson[] recipients;
		try {
			recipients = findNotificationRecipients(context);
		} catch (SQLException sqle) {
			log.warn("Can't notify thesis admins of embargo that's about to expire.", sqle);
			return;
		}
		// Send email to thesis administrators
		try {
			// Get some basic metadata
			DCValue[] titles = item.getMetadata(MetadataSchema.DC_SCHEMA, "title", null, Item.ANY);
			DCValue[] authors = item.getMetadata(MetadataSchema.DC_SCHEMA, "contributor", "author", Item.ANY);
			String title = titles.length > 0 ? titles[0].value : "no title";
			String author = authors.length > 0 ? authors[0].value : "no authors";
			// Send email
			Email emailmsg = ConfigurationManager.getEmail(I18nUtil.getEmailFilename(context.getCurrentLocale(), EMAIL_TEMPLATE_NOTIFY_ADVANCE_EXPIRY));
			for (EPerson recipient : recipients) {
				emailmsg.addRecipient(recipient.getEmail());
			}

			String itemIdentifier = "[workflow item]";
			String url = "the item's workflow screen";
			if (item.getHandle() != null) {
				itemIdentifier = item.getHandle();
				try {
					String link = HandleManager.resolveToURL(context, itemIdentifier);
					if (link != null)
						url = link;
				} catch (SQLException ex) {
					log.warn("can't determine url to item from handle " + itemIdentifier, ex);
				}
			}
			emailmsg.addArgument(itemIdentifier);
			emailmsg.addArgument(title);
			emailmsg.addArgument(author);
			emailmsg.addArgument(liftDate.toString());
			emailmsg.addArgument(url);
			emailmsg.send();
		} catch (IOException ioe) {
			log.warn("Problem sending notification email when detecting embargo that's about to expire", ioe);
		} catch (MessagingException me) {
			log.warn("Problem sending notification email when detecting embargo that's about to expire", me);
		}
	}


	private static void notifyPermissionsIncorrect(Context context, Item item) throws SQLException {
		EPerson[] recipients = findNotificationRecipients(context);
		// Send email to thesis administrators
		try {
			// Get some basic metadata
			DCValue[] titles = item.getMetadata(MetadataSchema.DC_SCHEMA, "title", null, Item.ANY);
			DCValue[] authors = item.getMetadata(MetadataSchema.DC_SCHEMA, "contributor", "author", Item.ANY);
			String title = titles.length > 0 ? titles[0].value : "no title";
			String author = authors.length > 0 ? authors[0].value : "no authors";
			// Send email
			Email emailmsg = ConfigurationManager.getEmail(I18nUtil.getEmailFilename(context.getCurrentLocale(), EMAIL_TEMPLATE_NOTIFY_PERMISSIONS));
			for (EPerson recipient : recipients) {
				emailmsg.addRecipient(recipient.getEmail());
			}
			String itemIdentifier = "[workflow item]";
			String url = "the item's workflow screen";
			if (item.getHandle() != null) {
				itemIdentifier = item.getHandle();
				try {
					String link = HandleManager.resolveToURL(context, itemIdentifier);
					if (link != null)
						url = link;
				} catch (SQLException ex) {
					log.warn("can't determine url to item from handle " + itemIdentifier, ex);
				}
			}
			emailmsg.addArgument(itemIdentifier);
			emailmsg.addArgument(title);
			emailmsg.addArgument(author);
			DCDate liftDate = EmbargoManager.getEmbargoTermsAsDate(context, item);
			emailmsg.addArgument(liftDate);
			emailmsg.addArgument(url);
			emailmsg.send();
		} catch (Exception ex) {
			log.warn("Problem sending notification email when warning about policies of embargoed item", ex);
		}
	}

	private static EPerson[] findNotificationRecipients(Context context) throws SQLException {
		EPerson[] emptyArray = new EPerson[0];
		if (ConfigurationManager.getProperty("lconz-event", EMBARGO_NOTIFICATION_GROUP_ID) == null) {
			return emptyArray;
		}
		int recipientsGroupID = ConfigurationManager.getIntProperty("lconz-event", EMBARGO_NOTIFICATION_GROUP_ID);
		Group recipientGroup = Group.find(context, recipientsGroupID);
		if (recipientGroup == null) {
			return emptyArray;
		}
		// get a list of all epeople in group (or any subgroups)
		return Group.allMembers(context, recipientGroup);
	}
}
