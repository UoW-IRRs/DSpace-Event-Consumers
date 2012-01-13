package nz.ac.lconz.irr.event.consumer;

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
	private List<String> taskNames = new ArrayList<String>();
	private String queueName = "continually";
	private ArrayList<Item> toQueue;

	public void initialize() throws Exception {
		String taskConfig = ConfigurationManager.getProperty("lconz-event", getTasksProperty());
		if (taskConfig == null || "".equals(taskConfig)) {
			System.err.println("QueueTaskOnInstall: no configuration value found for tasks to queue, can't initialise.");
			return;
		}
		taskNames.addAll(Arrays.asList(taskConfig.split(",\\s*")));

		String queueConfig = ConfigurationManager.getProperty("lconz-event", getQueueProperty());
		if (queueConfig != null && !"".equals(queueConfig)) {
			queueName = queueConfig;
		}
	}

	public void consume(Context ctx, Event event) throws Exception {
		Item item = null;

		if (isApplicableEvent(event)) {
			item = findItem(ctx, event);
		}

		if (item == null) {
			// not applicable -> skip
			return;
		}

		if (toQueue == null) {
			toQueue = new ArrayList<Item>();
		}
		toQueue.add(item);
	}

	abstract Item findItem(Context ctx, Event event) throws SQLException;

	abstract boolean isApplicableEvent(Event event);

	public void end(Context ctx) throws Exception {
		if (toQueue != null && !toQueue.isEmpty()) {
			for (String taskName : taskNames) {
				Curator curator = new Curator().addTask(taskName);
				for (Item item : toQueue) {
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
