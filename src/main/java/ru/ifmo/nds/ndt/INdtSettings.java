package ru.ifmo.nds.ndt;

import ru.ifmo.nds.util.QuickSelect;

public interface INdtSettings {
    int getBucketCapacity();

    int getDimensionsCount();

    default QuickSelect getQuickSelect() {
        return new QuickSelect();
    }
}
