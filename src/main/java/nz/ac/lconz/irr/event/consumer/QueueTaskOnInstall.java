package nz.ac.lconz.irr.event.consumer;

import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.curate.Curator;
import org.dspace.event.Consumer;
import org.dspace.event.Event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Andrea Schweer schweer@waikato.ac.nz
 *
 * Event consumer that queues curation tasks when an item is installed into the archive.
 *
 * Event consumers cannot make changes to their subject. Consequently, whenever a change to the item is desired on
 * installation, this needs to be performed by a curation task.
 *
 * This consumer queues up the curation tasks given in the configuration file via queue.install.tasks
 * (comma separated list) into the task queue named in the configuration file via queue.install.name
 * (default: continually). This task queue should then be run regularly and quite often, eg using a cronjob that runs
 * every minute or two.
 *
 */
public class QueueTaskOnInstall implements Consumer {
	List<String> taskNames = new ArrayList<String>();
	String queueName = "continually";

	public void initialize() throws Exception {
		String taskConfig = ConfigurationManager.getProperty("lconz-event", "queue.install.tasks");
		if (taskConfig == null || "".equals(taskConfig)) {
			System.err.println("QueueTaskOnInstall: no configuration value found for tasks to queue, can't initialise.");
			return;
		}
		taskNames.addAll(Arrays.asList(taskConfig.split(",\\s*")));

		String queueConfig = ConfigurationManager.getProperty("lconz-event", "queue.install.name");
		if (queueConfig != null && !"".equals(queueConfig)) {
			queueName = queueConfig;
		}
	}

	public void consume(Context ctx, Event event) throws Exception {
		if (event.getSubjectType() != Constants.ITEM || event.getEventType() != Event.INSTALL) {
			return; // wrong type of dso or of event -> ignore
		}
		Item item = (Item) event.getSubject(ctx);
		for (String taskName : taskNames) {
			new Curator().addTask(taskName).queue(ctx, item.getHandle(), queueName);
		}
	}

	public void end(Context ctx) throws Exception {
	}

	public void finish(Context ctx) throws Exception {
	}
}
