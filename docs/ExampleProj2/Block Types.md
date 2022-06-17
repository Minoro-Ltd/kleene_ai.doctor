# Warehouse Query **(deprecated)**

**This is deprecated, the  `:incremental` block should be used instead.**

Gets the max value for a given column (in the incremental field on the given report) in the destination table designated by the extract:

![Untitled](../../Untitled2.png)

```clojure
{:id   :warehouse-response
 :type :warehouse-query
 :next :next-url}

alternative options:
{:id    :warehouse-response
 :type  :select-max-from-warehouse
 :field "updated_at"
 :next  :next-url}
or
{:id    :warehouse-response
 :type  :warehouse-query
 :field "updated_at"
 :next  :next-url}
```

Will return the value of SELECT MAX(updated_at) FROM <<destination-table>>;
