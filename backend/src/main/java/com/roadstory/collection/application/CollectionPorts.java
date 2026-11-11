package com.roadstory.collection.application;

import com.roadstory.collection.domain.CollectionJob;
import com.roadstory.collection.domain.DataSource;
import com.roadstory.collection.domain.RawRecord;
import java.util.Optional;

public final class CollectionPorts {
    private CollectionPorts() {
    }

    public interface DataSourceRepository {
        Optional<DataSource> findBySourceCode(String sourceCode);
    }

    public interface CollectionJobRepository {
        CollectionJob save(CollectionJob job);
    }

    public interface RawRecordRepository {
        RawRecordSaveResult saveIfChanged(RawRecord record);
    }

    public enum RawRecordSaveResult {
        CREATED, UPDATED, UNCHANGED
    }
}
