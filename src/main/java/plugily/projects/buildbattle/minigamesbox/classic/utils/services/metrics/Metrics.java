package plugily.projects.buildbattle.minigamesbox.classic.utils.services.metrics;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.Callable;

public class Metrics {

    public Metrics(JavaPlugin plugin, int serviceId) {

    }

    public void addCustomChart(CustomChart chart) {

    }

    public abstract static class CustomChart {

        protected CustomChart(String chartId) {

        }

    }

    public static class SimplePie extends CustomChart {

        public SimplePie(String chartId, Callable<String> callable) {

            super(chartId);

        }

    }

    public static class AdvancedPie extends CustomChart {

        public AdvancedPie(String chartId, Callable<Map<String, Integer>> callable) {

            super(chartId);

        }

    }

    public static class DrilldownPie extends CustomChart {

        public DrilldownPie(String chartId, Callable<Map<String, Map<String, Integer>>> callable) {

            super(chartId);

        }

    }

    public static class SingleLineChart extends CustomChart {

        public SingleLineChart(String chartId, Callable<Integer> callable) {

            super(chartId);

        }

    }

    public static class MultiLineChart extends CustomChart {

        public MultiLineChart(String chartId, Callable<Map<String, Integer>> callable) {

            super(chartId);

        }

    }

    public static class SimpleBarChart extends CustomChart {

        public SimpleBarChart(String chartId, Callable<Map<String, Integer>> callable) {

            super(chartId);

        }

    }

    public static class AdvancedBarChart extends CustomChart {

        public AdvancedBarChart(String chartId, Callable<Map<String, int[]>> callable) {

            super(chartId);

        }

    }

}
