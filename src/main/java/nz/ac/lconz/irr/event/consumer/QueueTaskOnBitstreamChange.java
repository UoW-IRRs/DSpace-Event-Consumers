package nz.ac.lconz.irr.event.consumer;

import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.event.Event;

import java.sql.SQLException;

/**
 *  @author Andrea Schweer schweer@waikato.ac.nz for the LCoNZ IRRs
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

	@Override
	Item findItem(Context ctx, Event event) throws SQLException {
		if (event.getSubject(ctx) == null) {
			return null;
		}

		if (event.getSubjectType() == Constants.ITEM) {
			return (Item) event.getSubject(ctx);
		}

		Item result = null;
		Object parent = event.getSubject(ctx).getParentObject();
		if (parent != null && parent instanceof Item) {
			result = (Item) parent;
		}
		if (result != null && result.isArchived()) {
			return result;
		}
		return null;
	}

	@Override
	boolean isApplicableEvent(Context ctx, Event event) throws SQLException {
		int eventType = event.getEventType();
		return (event.getSubjectType() == Constants.BUNDLE && (eventType == Event.ADD || eventType == Event.REMOVE))
				|| (event.getSubjectType() == Constants.ITEM && eventType == Event.REMOVE)
				&& (findItem(ctx, event) != null);
	}

	String getTasksProperty() {
		return "queue.bitstreamchange.tasks";
	}

	String getQueueProperty() {
		return "queue.bitstreamchange.name";
	}
}
