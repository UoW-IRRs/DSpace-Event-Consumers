package nz.ac.lconz.irr.event.hook;

import nz.ac.lconz.irr.event.util.CurationHelper;
import org.apache.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.core.Context;

import java.io.IOException;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz for LCoNZ
 */
public class AccessLevelHooks {
	private static final Logger log = Logger.getLogger(AccessLevelHooks.class);


	private static final String ACCESS_CHANGED_CURATION_QUEUE_NAME = "queue.access.changed.name";
	private static final String ACCESS_CHANGED_CURATION_TASKS = "queue.access.changed.tasks";

	public static void atAccessLevelChanged(Context context, Item item) {

		try {
			queueForCuration(context, item, ACCESS_CHANGED_CURATION_TASKS, ACCESS_CHANGED_CURATION_QUEUE_NAME);
		} catch (IOException e) {
			log.warn("Caught exception while trying to queue curation task when access level was changed for item id=" + item.getID());
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
}
