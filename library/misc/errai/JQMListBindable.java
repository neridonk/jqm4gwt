package com.vx.sw.client.jqm4gwt;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jboss.errai.databinding.client.BindableListChangeHandler;
import org.jboss.errai.databinding.client.BindableListWrapper;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.ComplexPanel;
import com.google.gwt.user.client.ui.HasValue;
import com.sksamuel.jqm4gwt.list.JQMList;
import com.sksamuel.jqm4gwt.list.JQMListDivider;
import com.sksamuel.jqm4gwt.list.JQMListItem;

/**
 * The same idea as {@link org.jboss.errai.ui.client.widget.ListWidget} but adopted for JQM4GWT.
 * <br> Binds model's List&lt;M> with UI list items generated by some Renderer implementation.
 */
public class JQMListBindable<M> extends JQMList
        implements HasValue<List<M>>, BindableListChangeHandler<M> {

    public interface JQMListItemHandler {
        void onDivider(JQMListDivider divider);
        void onListItem(JQMListItem item);
    }

    public static void forEachListItem(List<? extends ComplexPanel> uiItems, JQMListItemHandler handler) {
        if (handler == null || uiItems == null || uiItems.isEmpty()) return;
        for (ComplexPanel i : uiItems) {
            if (i instanceof JQMListItem) {
                handler.onListItem((JQMListItem) i);
            } else if (i instanceof JQMListDivider) {
                handler.onDivider((JQMListDivider) i);
            }
        }
    }

    public interface Renderer<M> {

        List<? extends ComplexPanel> addItem(JQMListBindable<M> list, M item);

        /**
         * In some cases could be implemented as simple as addItem().
         */
        List<? extends ComplexPanel> insertItem(JQMListBindable<M> list, int dataIndex, M item);

        /**
         * @param uiItems - the same items which have been returned earlier by addItem() / insertItem()
         * for this particular model item.
         */
        void removeItem(JQMListBindable<M> list, M item, List<? extends ComplexPanel> uiItems);

        /**
         * There are many possible reasons why/when this method is called:
         * <br> 1. data item's content changed, i.e. oldItem == newItem
         * <br> 2. new data item is replacing old one
         * <br> 3. sort() is called, so data items positions are exchanging (no real remove/add to the list)
         *
         * @param oldUiItems - the same items which have been returned earlier by addItem() / insertItem()
         * for this particular model item.
         * <br>
         * @param oldItemDeleted - means item is not in data/model anymore, otherwise probably
         * it was just moved to a different position, for example by sort().
         * <br>
         * @param newUiItems - if newItem is already in data/model, then these ui items were returned
         * earlier by addItem() / insertItem(). Otherwise is just null.
         * <br>
         * @return - must return new UI items for this model item.
         */
        List<? extends ComplexPanel> itemChanged(JQMListBindable<M> list, int dataIndex,
                M oldItem, List<? extends ComplexPanel> oldUiItems, boolean oldItemDeleted,
                M newItem, List<? extends ComplexPanel> newUiItems);

        /**
         * Called after all add/remove operations are finished, right before list.refresh() call.
         */
        void onBeforeRefresh(JQMListBindable<M> list);

        /**
         * Called after all add/remove operations and list.refresh() are finished.
         */
        void onAfterRefresh(JQMListBindable<M> list);
    }

    public static abstract class BaseRenderer<M> implements Renderer<M> {

        @Override
        public List<? extends ComplexPanel> insertItem(JQMListBindable<M> list, int dataIndex, M item) {
            return addItem(list, item);
        }

        @Override
        public List<? extends ComplexPanel> itemChanged(JQMListBindable<M> list, int dataIndex,
                M oldItem, List<? extends ComplexPanel> oldUiItems, boolean oldItemDeleted,
                M newItem, List<? extends ComplexPanel> newUiItems) {

            if (oldItemDeleted) removeItem(list, oldItem, oldUiItems);
            if (newUiItems != null) removeItem(list, newItem, newUiItems);
            return insertItem(list, dataIndex, newItem);
        }

        @Override
        public void onBeforeRefresh(JQMListBindable<M> list) {
        }

        @Override
        public void onAfterRefresh(JQMListBindable<M> list) {
        }
    }

    public static abstract class ListRenderer<M> extends BaseRenderer<M> {

        private final boolean showEmptyMsg;

        private JQMListItem emptyMsg = null;

        private boolean unstableUiIndex;

        public ListRenderer(boolean showEmptyMsg, boolean unstableUiIndex) {
            this.showEmptyMsg = showEmptyMsg;
            this.unstableUiIndex = unstableUiIndex;
        }

        public ListRenderer() {
            this(true/*showEmptyMsg*/, false/*unstableUiIndex*/);
        }

        public ListRenderer(boolean showEmptyMsg) {
            this(showEmptyMsg, false/*unstableUiIndex*/);
        }

        protected abstract JQMListItem createListItem(M item);

        /**
         * @param item
         * @return - list of JQMListDivider and/or JQMListItem, needed in case of complex model item
         * when using createListItem() just is not enough.
         */
        protected List<? extends ComplexPanel> createListItems(M item) {
            return null;
        }

        protected String getEmptyText() {
            return "-----";
        }

        private void addEmptyMsg(JQMListBindable<M> list) {
            if (!showEmptyMsg) return;
            List<JQMListItem> items = list.getItems();
            if (items == null || items.isEmpty()) {
                JQMListDivider d = (JQMListDivider) list.addDivider("");
                emptyMsg = list.addItem(getEmptyText());
                d.setTag(emptyMsg);
            }
        }

        private void removeEmptyMsg(JQMListBindable<M> list) {
            if (!showEmptyMsg || emptyMsg == null) return;
            list.removeItem(emptyMsg);
            list.removeDividerByTag(emptyMsg);
            emptyMsg = null;
        }

        private List<? extends ComplexPanel> addItemIntern(final JQMListBindable<M> list, M item,
                                                           final int position) {
            removeEmptyMsg(list);
            JQMListItem li = createListItem(item);
            if (li != null) {
                if (position >= 0) list.addItem(position, li);
                else list.appendItem(li);
                return Collections.singletonList(li);
            } else {
                List<? extends ComplexPanel> lst = createListItems(item);
                forEachListItem(lst, new JQMListItemHandler() {
                    private int j = position;

                    @Override
                    public void onListItem(JQMListItem item) {
                        if (position >= 0) list.addItem(j++, item);
                        else list.appendItem(item);
                    }

                    @Override
                    public void onDivider(JQMListDivider divider) {
                        if (position >= 0) list.addDivider(j++, divider);
                        else list.appendDivider(divider);
                    }
                });
                return lst;
            }
        }

        @Override
        public List<? extends ComplexPanel> addItem(final JQMListBindable<M> list, M item) {
            return addItemIntern(list, item, -1);
        }

        @Override
        public List<? extends ComplexPanel> insertItem(JQMListBindable<M> list, int dataIndex, M item) {
            if (unstableUiIndex) return addItem(list, item);

            List<M> data = list.getDataItems();
            if (dataIndex < data.size() - 1) {
                M oldItem = data.get(dataIndex + 1);
                int oldPos = list.getUiIndex(oldItem);
                return addItemIntern(list, item, oldPos);
            }
            return addItem(list, item);
        }

        @Override
        public List<? extends ComplexPanel> itemChanged(JQMListBindable<M> list, int dataIndex,
                M oldItem, List<? extends ComplexPanel> oldUiItems, boolean oldItemDeleted,
                M newItem, List<? extends ComplexPanel> newUiItems) {

            final int oldPos = unstableUiIndex ? -1 : list.getUiIndex(oldItem);
            if (oldItemDeleted) removeItem(list, oldItem, oldUiItems);
            if (newUiItems != null) removeItem(list, newItem, newUiItems);
            return addItemIntern(list, newItem, oldPos);
        }

        @Override
        public void removeItem(final JQMListBindable<M> list, M item, List<? extends ComplexPanel> uiItems) {
            forEachListItem(uiItems, new JQMListItemHandler() {

                @Override
                public void onDivider(JQMListDivider divider) {
                    list.removeDivider(divider);
                }

                @Override
                public void onListItem(JQMListItem item) {
                    list.removeItem(item);
                    list.removeDividerByTag(item);
                }});

            addEmptyMsg(list);
        }

        @Override
        public void onBeforeRefresh(JQMListBindable<M> list) {
            addEmptyMsg(list);
            list.recreate();
        }

        public boolean isUnstableUiIndex() {
            return unstableUiIndex;
        }

        /**
         * Used during insertItem() and itemChanged() processing.
         * <br>Stable ui index means that there are no manual sort() or items exchange operations
         * over underlying/binded data items. So previous data item's ui index can be used as
         * base for current data item visual placement.
         */
        public void setUnstableUiIndex(boolean unstableUiIndex) {
            this.unstableUiIndex = unstableUiIndex;
        }
    }

    /**
     * An "ordered" JQMListBindable (in terms of Html, i.e. only 1, 2, 3, ... position indicator, no real data ordering)
     */
    public static class Ordered<M> extends JQMListBindable<M> {
        public Ordered() {
           super(true);
        }
    }

    /**
     * An "unordered" JQMListBindable (in terms of Html, i.e. only no visual position indicator)
     */
    public static class Unordered<M> extends JQMListBindable<M> {
        public Unordered() {
           super(false);
        }
    }

    private Renderer<M> renderer;

    private BindableListWrapper<M> dataItems;

    private final Map<M, List<? extends ComplexPanel>> dataToUI = new HashMap<>();

    private boolean valueChangeHandlerInitialized;

    public JQMListBindable() {
        this(false/*ordered*/);
    }

    public JQMListBindable(boolean ordered) {
        super(ordered);
    }

    @Override
    public void clear() {
        super.clear();
        dataToUI.clear();
    }

    /**
     * Sets the list of model objects.
     * The list will be wrapped in an {@link BindableListWrapper} to make direct changes
     * to the list observable.
     *
     * @param items - The list of model objects. If null or empty all existing items will be removed.
     */
    public void setDataItems(List<M> items) {
        boolean changed = this.dataItems != items;
        if (items == null) {
            this.dataItems = null;
        } else {
            this.dataItems = items instanceof BindableListWrapper ? (BindableListWrapper<M>) items
                                                                  : new BindableListWrapper<M>(items);
            if (changed) this.dataItems.addChangeHandler(this);
        }
        addDataItems();
    }

    public List<M> getDataItems() {
        return dataItems;
    }

    private void addDataItems() {
        clear();
        if (dataItems != null && !dataItems.isEmpty()) {
            for (final M item : dataItems) {
                addDataItem(item);
            }
        }
        doRefresh();
    }

    private void addDataItem(final M item) {
        List<? extends ComplexPanel> ui = renderer.addItem(this, item);
        dataToUI.put(item, ui);
    }

    private void insertDataItem(final int index, final M item) {
        List<? extends ComplexPanel> ui = renderer.insertItem(this, index, item);
        dataToUI.put(item, ui);
    }

    private void removeDataItem(final M item) {
        List<? extends ComplexPanel> ui = dataToUI.get(item);
        renderer.removeItem(this, item, ui);
        dataToUI.remove(item);
    }

    private void dataItemChanged(final int index, final M oldItem, final M newItem) {
        // in case of sort() item can appear as newItem first, then later as oldItem
        // Example: 0: 8>1, 1: 6>2, 2: 4>3, 3: 1>4, 4: 7>5, 5: 3>6, 6: 2>7, 7: 5>8

        List<? extends ComplexPanel> oldUi = dataToUI.get(oldItem);
        List<? extends ComplexPanel> newUi = dataToUI.get(newItem);
        boolean oldInData = dataItems.contains(oldItem);

        newUi = renderer.itemChanged(this, index, oldItem, oldUi, !oldInData, newItem, newUi);

        if (oldItem != newItem && !oldInData) dataToUI.remove(oldItem);
        dataToUI.put(newItem, newUi);
    }

    private void doRefresh() {
        renderer.onBeforeRefresh(this);
        refresh();
        renderer.onAfterRefresh(this);
    }

    public M getDataItem(JQMListItem uiItem) {
        if (uiItem == null) return null;
        for (Entry<M, List<? extends ComplexPanel>> i : dataToUI.entrySet()) {
            List<? extends ComplexPanel> uiItems = i.getValue();
            if (uiItems.contains(uiItem)) return i.getKey();
        }
        return null;
    }

    public List<? extends ComplexPanel> getUiItems(M dataItem) {
        if (dataItem == null) return null;
        return dataToUI.get(dataItem);
    }

    /**
     * @return - first visual position for this data item, or -1 otherwise.
     */
    public int getUiIndex(M dataItem) {
        List<? extends ComplexPanel> ui = getUiItems(dataItem);
        if (ui == null || ui.isEmpty()) return -1;
        ComplexPanel item = ui.get(0);
        if (item instanceof JQMListItem) {
            return getItems().indexOf(item);
        } else if (item instanceof JQMListDivider) {
            return findDividerIdx((JQMListDivider) item);
        } else {
            return -1;
        }
    }

    @Override
    public HandlerRegistration addValueChangeHandler(ValueChangeHandler<List<M>> handler) {
        if (!valueChangeHandlerInitialized) {
            valueChangeHandlerInitialized = true;
            addDomHandler(new ChangeHandler() {
                @Override
                public void onChange(ChangeEvent event) {
                    ValueChangeEvent.fire(JQMListBindable.this, getValue());
                }
            }, ChangeEvent.getType());
        }
        return addHandler(handler, ValueChangeEvent.getType());
    }

    @Override
    public List<M> getValue() {
        return dataItems;
    }

    @Override
    public void setValue(List<M> value) {
        setValue(value, false);
    }

    @Override
    public void setValue(List<M> value, boolean fireEvents) {
        List<M> oldValue = getValue();
        // if list changed, BindibleProxy will call updateWidgetsAndFire() -> this.setValue()
        // but we may already listen to passed list and in such case should not call setDataItems()
        if (oldValue != value) setDataItems(value);
        if (fireEvents) {
            ValueChangeEvent.fireIfNotEqual(this, oldValue, value);
        }
    }

    @Override
    public void onItemAdded(List<M> oldList, M item) {
        addDataItem(item);
        doRefresh();
    }

    @Override
    public void onItemAddedAt(List<M> oldList, int index, M item) {
        insertDataItem(index, item);
        doRefresh();
    }

    @Override
    public void onItemsAdded(List<M> oldList, Collection<? extends M> items) {
        for (M m : items) {
            addDataItem(m);
        }
        doRefresh();
    }

    @Override
    public void onItemsAddedAt(List<M> oldList, int index, Collection<? extends M> item) {
        Iterator<? extends M> iter = item.iterator();
        int pos = index;
        while (iter.hasNext()) {
            M i = iter.next();
            insertDataItem(pos, i);
            pos++;
        }
        doRefresh();
    }

    @Override
    public void onItemsCleared(List<M> oldList) {
        clear();
        doRefresh();
    }

    @Override
    public void onItemRemovedAt(List<M> oldList, int index) {
        removeDataItem(oldList.get(index));
        doRefresh();
    }

    @Override
    public void onItemsRemovedAt(List<M> oldList, List<Integer> indexes) {
        for (Integer index : indexes) {
            removeDataItem(oldList.get(index));
        }
        doRefresh();
    }

    @Override
    public void onItemChanged(List<M> oldList, int index, M item) {
        dataItemChanged(index, oldList.get(index), item);
        doRefresh();
    }

    /**
     * Needed in case of renderer changed, so list has to be visually reconstructed by new renderer.
     */
    public void rerender() {
        clear();
        if (dataItems != null) {
            for (M m : dataItems) {
                addDataItem(m);
            }
        }
        doRefresh();
    }

    public Renderer<M> getRenderer() {
        return renderer;
    }

    public void setRenderer(Renderer<M> renderer) {
        this.renderer = renderer;
    }

}
