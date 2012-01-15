package nz.ac.lconz.irr.event.consumer;

import org.apache.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.curate.Curator;
import org.dspace.event.Consumer;
import org.dspace.event.Event;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *  @author Andrea Schweer schweer@waikato.ac.nz for LCoNZ IRR project
 *
 * Abstract event consumer that queues curation tasks when specific events occur.
 */
public abstract class QueueTaskOnEvent implements Consumer {
	private static Logger log = Logger.getLogger(QueueTaskOnEvent.class);

	private List<String> taskNames = new ArrayList<String>();
	private String queueName = "continually";
	private ArrayList<Item> toQueue;

	public void initialize() throws Exception {
		String taskConfig = ConfigurationManager.getProperty("lconz-event", getTasksProperty());
		if (taskConfig == null || "".equals(taskConfig)) {
			log.error("QueueTaskOnEvent: no configuration value found for tasks to queue (" + getTasksProperty() + "), can't initialise.");
			return;
		}
		taskNames.addAll(Arrays.asList(taskConfig.split(",\\s*")));

		String queueConfig = ConfigurationManager.getProperty("lconz-event", getQueueProperty());
		if (queueConfig != null && !"".equals(queueConfig)) {
			queueName = queueConfig;
			log.info("Using queue name " + queueName);
		} else {
			log.info("No queue name specified, using default: " + queueName);
		}
	}

	public void consume(Context ctx, Event event) throws Exception {
		Item item = null;

		if (isApplicableEvent(event)) {
			item = findItem(ctx, event);
		} else {
			log.info("Event is not applicable, skipping");
		}

		if (item == null) {
			// not applicable -> skip
			log.info("Can't find item to work on, skipping");
			return;
		}

		if (toQueue == null) {
			toQueue = new ArrayList<Item>();
		}
		toQueue.add(item);
		log.info("Adding item " + item.getHandle() + " to list of items to queue");
	}

	abstract Item findItem(Context ctx, Event event) throws SQLException;

	abstract boolean isApplicableEvent(Event event);

	public void end(Context ctx) throws Exception {
		if (toQueue != null && !toQueue.isEmpty()) {
			log.info("Actually queueing " + toQueue.size() + " items for curation");
			for (String taskName : taskNames) {
				Curator curator = new Curator().addTask(taskName);
				for (Item item : toQueue) {
					log.info("Queued item " + item.getHandle() + " for curation in queue " + queueName + ", task " + taskName);
					curator.queue(ctx, item.getHandle(), queueName);
				}
			}
		}
		toQueue = null;
	}

	public void finish(Context ctx) throws Exception {
	}

	abstract String getTasksProperty();

	abstract String getQueueProperty();
}
