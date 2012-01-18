package nz.ac.lconz.irr.event.util;

import nz.ac.lconz.irr.event.consumer.QueueTaskOnEvent;
import org.apache.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.curate.Curator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Helper class to deal with curation tasks.
 * @author Andrea Schweer schweer@waikato.ac.nz for LCoNZ IRR project
 */
public class CurationHelper {
	private static Logger log = Logger.getLogger(CurationHelper.class);

	private List<String> taskNames = new ArrayList<String>();
	private String queueName = "continually";
	private ArrayList<Item> toQueue;

	public void initQueueName(String queueProperty) {
		String queueConfig = ConfigurationManager.getProperty("lconz-event", queueProperty);
		if (queueConfig != null && !"".equals(queueConfig)) {
			queueName = queueConfig;
			log.info("Using queue name " + queueName);
		} else {
			log.info("No queue name specified, using default: " + queueName);
		}
	}

	public void initTaskNames(String tasksProperty) {
		List<String> taskNamesList;
		String taskConfig = ConfigurationManager.getProperty("lconz-event", tasksProperty);
		if (taskConfig == null || "".equals(taskConfig)) {
			taskNamesList = null;
		} else {
			taskNamesList = Arrays.asList(taskConfig.split(",\\s*"));
		}
		taskNames.addAll(taskNamesList);
	}

	public void addToQueue(Item item) {
		if (toQueue == null) {
			toQueue = new ArrayList<Item>();
		}
		toQueue.add(item);
		log.info("Adding item " + item.getHandle() + " to list of items to queue");
	}

	public void queueForCuration(Context ctx) throws IOException {
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

	public boolean hasTaskNames() {
		return !taskNames.isEmpty();
	}
}
