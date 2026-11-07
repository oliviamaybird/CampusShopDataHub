package de.campusshop.cw1.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.campusshop.cw1.config.StorageProperties;
import de.campusshop.cw1.exception.StorageException;
import de.campusshop.cw1.model.Order;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * JSON-file backed {@link OrderRepository}.
 *
 * <p>TODO (student): implement file persistence with Jackson {@code ObjectMapper} and
 * {@code Path}/{@code Files} (Week 5; CW1 spec §5). Store <b>all orders in ONE file as a single
 * JSON array</b> — not one file per order. Suggested approach: hold the orders in the in-memory
 * {@code ordersById} map for fast lookup and rewrite the whole array after each mutation, under the
 * {@code lock}. Create the parent directory and an empty {@code []} file if missing; write
 * atomically (temp file + {@code Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)}) so a crash never
 * leaves a half-written store (Appendix D.2). Date serialisation is handled by the {@code ObjectMapper}
 * configuration (Appendix D.3).
 */
@Repository
public class FileOrderRepository implements OrderRepository {

    private static final Logger log = LoggerFactory.getLogger(FileOrderRepository.class);

    private final ObjectMapper objectMapper;
    private final Path storagePath;
    private final Map<String, Order> ordersById = new LinkedHashMap<>();
    private final Object lock = new Object();

    public FileOrderRepository(ObjectMapper objectMapper, StorageProperties properties) {
        this.objectMapper = objectMapper;
        this.storagePath = Path.of(properties.file());
    }

    /** Initialise the store: create parent dir + empty file if needed, then load existing orders. */
    @PostConstruct
    public void load() {
        synchronized (lock) {
            try {
                if (storagePath.getParent() != null) {
                    Files.createDirectories(storagePath.getParent());
                    // if there is no directory containing the file, create one
                }
                if (!Files.exists(storagePath)) {
                    Files.writeString(storagePath, "[]");
                    // if the file itself doesn't exist, we create it (and write empty JSON array into it)
                }
                List<Order> existing = //store list
                        objectMapper.readValue( // read file and convert JSON into Java objects
                                storagePath.toFile(), // converts path into file object
                                new TypeReference<List<Order>>() {} // telling Jackson to convert JSON into list of Order objects
                        );
                        ordersById.clear(); // clear existing entires in orderById
                for (Order order : existing) {
                    //loop through all orders
                    ordersById.put(order.orderId(), order);
                    // store in orderById hashmap with orderId as lookupable key
                }
                log.info("Loaded {} order(s) from {}", ordersById.size(), storagePath.toAbsolutePath());
            } catch (IOException ex) {
                throw new StorageException("Could not initialise order store at " + storagePath, ex);
            }
        }
    }

    @Override
    public Order save(Order order) {
        synchronized (lock) { //atomic processing for reliability
            ordersById.put(order.orderId(), order);
            flush();
            return order;
        }
    }

    private void flush() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(storagePath.toFile(), new ArrayList<>(ordersById.values()));
        } catch (IOException ex) {
            throw new StorageException("Could not write order store at " + storagePath, ex);
        }
    }

    @Override
    public Optional<Order> findById(String orderId) {
        synchronized (lock) {
            return Optional.ofNullable(ordersById.get(orderId));
            // optional good for use when result is likely to be null
        }
    }

    @Override
    public List<Order> findAll() {
        synchronized (lock) {
            return new ArrayList<>(ordersById.values());
        }
    }

    @Override
    public List<Order> findByCustomerId(String customerId) {
        synchronized (lock) {
            return ordersById.values().stream().filter(order -> order.customerId().equals(customerId)).toList();
        }
    }

    @Override
    public boolean deleteById(String orderId) {
        synchronized (lock) {
            if (ordersById.containsValue(orderId)){
                ordersById.remove(orderId);
                flush();
                return true;
            }
            return false;
        }
    }

    @Override
    public boolean existsByExternalOrderId(String externalOrderId) {
        synchronized (lock) {
            return ordersById.values().stream().anyMatch(order -> order.externalOrderId().equals(externalOrderId));
            // anyMatch returns true/false depending on if an order with that externalOrderId() exists or not
        }
    }
}
