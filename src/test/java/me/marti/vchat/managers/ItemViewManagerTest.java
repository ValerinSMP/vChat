package me.marti.vchat.managers;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemViewManagerTest {

    @Test
    void cacheStoresSnapshotIndependentFromOriginalItem() {
        ItemViewManager manager = new ItemViewManager(null, () -> 300L);
        ItemStack original = mock(ItemStack.class);
        ItemStack storedSnapshot = mock(ItemStack.class);
        ItemStack returnedSnapshot = mock(ItemStack.class);
        when(original.clone()).thenReturn(storedSnapshot);
        when(storedSnapshot.clone()).thenReturn(returnedSnapshot);

        UUID itemId = manager.cacheItem(original);
        ItemStack cached = manager.getItem(itemId);

        assertSame(returnedSnapshot, cached);
        verify(original).clone();
        verify(storedSnapshot).clone();
    }

    @Test
    void getItemReturnsCloneIndependentFromInternalCache() {
        ItemViewManager manager = new ItemViewManager(null, () -> 300L);
        ItemStack original = mock(ItemStack.class);
        ItemStack storedSnapshot = mock(ItemStack.class);
        ItemStack firstRead = mock(ItemStack.class);
        ItemStack secondRead = mock(ItemStack.class);
        when(original.clone()).thenReturn(storedSnapshot);
        when(storedSnapshot.clone()).thenReturn(firstRead, secondRead);

        UUID itemId = manager.cacheItem(original);
        ItemStack firstResult = manager.getItem(itemId);
        ItemStack secondResult = manager.getItem(itemId);

        assertSame(firstRead, firstResult);
        assertSame(secondRead, secondResult);
        assertNotSame(firstResult, secondResult);
    }
}
