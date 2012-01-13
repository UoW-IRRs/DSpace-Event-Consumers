package nz.ac.lconz.irr.event.consumer;

import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
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
 * Event consumer that queues curation tasks when an item's bitstreams are changed in any way.
 *
 * Event consumers cannot make changes to their subject. Consequently, whenever a change to the item is desired on
 * installation, this needs to be performed by a curation task.
 *
 * This consumer queues up the curation tasks given in the configuration file via queue.bitstreamchange.tasks
 * (comma separated list) into the task queue named in the configuration file via queue.bitstreamchange.name
 * (default: continually). This task queue should then be run regularly and quite often, eg using a cronjob that runs
 * every minute or two.
 *
 */
public class QueueTaskOnBitstreamChange extends QueueTaskOnEvent {


	Item findItem(Context ctx, Event event) throws SQLException {
		Object parent = event.getSubject(ctx).getParentObject();
		if (parent != null && parent instanceof Item) {
			return (Item) parent;
		}
		return null;
	}

	boolean isApplicableEvent(Event event) {
		if (event.getSubjectType() == Constants.BUNDLE) {
			return event.getEventType() == Event.ADD || event.getEventType() == Event.MODIFY;
		} else if (event.getSubjectType() == Constants.BITSTREAM) {
			return event.getEventType() == Event.MODIFY;
		}
		return false;
	}

	String getTasksProperty() {
		return "queue.bitstreamchange.tasks";
	}

	String getQueueProperty() {
		return "queue.bitstreamchange.name";
	}
}
