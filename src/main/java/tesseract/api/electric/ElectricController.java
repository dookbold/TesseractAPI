package tesseract.api.electric;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.*;
import tesseract.api.Absorber;
import tesseract.api.ConnectionType;
import tesseract.api.Controller;
import tesseract.graph.*;
import tesseract.util.Dir;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static tesseract.TesseractAPI.GLOBAL_ELECTRIC_EVENT;

/**
 * Class acts as a controller in the group of an electrical components.
 */
public final class ElectricController extends Controller<ElectricConsumer, IElectricCable, IElectricNode> {

    private final Object2IntMap<IElectricNode> obtains = new Object2IntLinkedOpenHashMap<>();

    /**
     * Creates instance of the controller.
     *
     * @param dim The dimension id.
     * @param group The group this controller handles.
     */
    public ElectricController(int dim, @Nonnull Group<IElectricCable, IElectricNode> group) {
        super(dim, group);
    }

    /**
     * Call on the updates to send energy.
     * <p>
     * Most of the magic going in producer class which acts as wrapper double iterator around controller map.
     * Firstly, method will look for the available producer and consumer.
     * Secondly, some amperage calculation is going using the consumer and producer data.
     * Thirdly, it will check the voltage and amperage for the single impulse by the lowest cost cable.
     * </p>
     * If that function will find corrupted cables, it will execute loop to find the corrupted cables and exit.
     * However, if corrupted cables wasn't found, it will looks for variate connection type and store the amp for that path.
     * After energy was send, loop will check the amp holder instances on ampers map to find cross-nodes where amps/voltage is exceed max limit.
     */
    @Override
    public void tick() {
        obtains.clear();
        absorbs.clear();

        for (Object2ObjectMap.Entry<IElectricNode, ObjectList<ElectricConsumer>> e : data.object2ObjectEntrySet()) {
            IElectricNode producer = e.getKey();
            int outputVoltage = producer.getOutputVoltage();
            int outputAmperage = producer.getOutputAmperage();
            if (outputAmperage <= 0) {
                continue;
            }

            for (ElectricConsumer consumer : e.getValue()) {
                int amperage = consumer.getRequiredAmperage(outputVoltage);

                // look up how much it already got
                amperage -= obtains.getInt(consumer.getConsumer());
                if (amperage <= 0) { // if this consumer received all the energy from the other producers
                    continue;
                }

                // remember amperes stored in this consumer
                amperage = Math.min(outputAmperage, amperage);
                obtains.put(consumer.getConsumer(), obtains.getInt(consumer.getConsumer()) + amperage);

                consumer.insert((outputVoltage - consumer.getLoss()) * (long) amperage, false);
                producer.extract(outputVoltage * (long) amperage, false);

                // If we are here, then path had some invalid cables which not suits the limits of amps/voltage
                if (!consumer.canHandle(outputVoltage, amperage) && consumer.getConnection() != ConnectionType.ADJACENT) { // Fast check by the lowest cost cable
                    // Find corrupt cables and return
                    for (Long2ObjectMap.Entry<IElectricCable> c : consumer.getFull()) {
                        IElectricCable cable = c.getValue();
                        long pos = c.getLongKey();

                        switch (cable.getHandler(outputVoltage, amperage)) {
                            case FAIL_VOLTAGE:
                                GLOBAL_ELECTRIC_EVENT.onCableOverVoltage(dim, pos, outputVoltage);
                                break;
                            case FAIL_AMPERAGE:
                                GLOBAL_ELECTRIC_EVENT.onCableOverAmperage(dim, pos, amperage);
                                break;
                        }
                    }
                    return;
                }

                // Stores the amp into holder for path only for variate connection
                if (consumer.getConnection() == ConnectionType.VARIATE) {
                    for (Long2ObjectMap.Entry<IElectricCable> c : consumer.getCross()) {
                        IElectricCable cable = c.getValue();
                        long pos = c.getLongKey();

                        Absorber a = absorbs.get(pos);
                        if (a == null) {
                            absorbs.put(pos, new Absorber(cable.getAmps(), amperage));
                        } else {
                            a.add(amperage);
                        }
                    }
                }

                outputAmperage -= amperage;
                if (outputAmperage <= 0)
                    break;
            }
        }

        for (Long2ObjectMap.Entry<Absorber> e : absorbs.long2ObjectEntrySet()) {
            Absorber absorber = e.getValue();
            long pos = e.getLongKey();

            if (absorber.isOver()) {
                GLOBAL_ELECTRIC_EVENT.onCableOverAmperage(dim, pos, absorber.get());
            }
        }
    }

    @Override
    protected void onMerge(@Nonnull IElectricNode producer, @Nonnull ObjectList<ElectricConsumer> consumers) {
        ObjectList<ElectricConsumer> existingConsumers = data.get(producer);
        for (ElectricConsumer c : consumers) {
            boolean found = false;
            for (ElectricConsumer ec : existingConsumers) {
                if (ec.getConsumer() == c.getConsumer()) {
                    found = true;
                    if (ec.getLoss() > c.getLoss()) {
                        ec.copy(c);
                    }
                }
                if (!found) existingConsumers.add(c);
            }
        }
    }

    @Override
    protected void onCheck(@Nonnull IElectricNode producer, @Nonnull ObjectList<ElectricConsumer> consumers, @Nullable Path<IElectricCable> path, long pos) {
        IElectricNode c = group.getNodes().get(pos).value();
        if (c.canInput()) {
            int voltage = producer.getOutputVoltage();
            if (voltage > c.getInputVoltage()) {
                GLOBAL_ELECTRIC_EVENT.onNodeOverVoltage(dim, pos, voltage);
            } else {
                ElectricConsumer consumer = new ElectricConsumer(c, path);
                if (producer.getOutputVoltage() > consumer.getLoss()) {
                    consumers.add(consumer);
                }
            }
        }
    }

    @Nonnull
    @Override
    @SuppressWarnings("unchecked")
    public ITickingController clone(@Nonnull INode group) {
        assert (group instanceof Group<?, ?>);
        return new ElectricController(dim, (Group<IElectricCable, IElectricNode>) group);
    }

    @Override
    protected boolean isValid(@Nonnull IElectricNode producer, @Nullable Dir direction) {
        return direction != null ? producer.canOutput(direction) : producer.canOutput() && producer.getOutputVoltage() > 0;
    }
}