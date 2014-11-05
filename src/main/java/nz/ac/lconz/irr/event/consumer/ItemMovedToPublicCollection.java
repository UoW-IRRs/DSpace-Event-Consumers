package nz.ac.lconz.irr.event.consumer;

import org.dspace.authorize.AuthorizeManager;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.Group;
import org.dspace.event.Event;

import java.sql.SQLException;

/**
 *  @author Andrea Schweer schweer@waikato.ac.nz for the LCoNZ IRRs
 *
 *  Event consumer that checks whether the event represents an item that has been moved to a publicly-readable collection.
 */
public class ItemMovedToPublicCollection extends QueueTaskOnEvent {
	@Override
	Item findItem(Context ctx, Event event) throws SQLException {
		return (Item) event.getObject(ctx);
	}

	@Override
	boolean isApplicableEvent(Context ctx, Event event) throws SQLException {
		if (event.getSubjectType() != Constants.COLLECTION || event.getObjectType() != Constants.ITEM || event.getEventType() != Event.ADD) {
			return false;
		}
		Collection destinationCollection = (Collection) event.getSubject(ctx);
		return anonymousCanRead(ctx, destinationCollection);
	}

	private boolean anonymousCanRead(Context ctx, DSpaceObject dso) throws SQLException {
		Group[] readGroups = AuthorizeManager.getAuthorizedGroups(ctx, dso, Constants.READ);
		for (Group group : readGroups) {
			if (group.getID() == 0) {
				return true;
			}
		}
		return false;
	}

	@Override
	String getTasksProperty() {
		return "queue.item_moved_public_coll.tasks";
	}

	@Override
	String getQueueProperty() {
		return "queue.item_moved_public_coll.name";
	}
}
