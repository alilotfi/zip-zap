package custom;

import com.motivewave.platform.sdk.common.*;
import com.motivewave.platform.sdk.common.desc.*;
import com.motivewave.platform.sdk.draw.Label;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@StudyHeader(
        namespace = "ir.alilo",
        id = "ZIG_ZAG",
        rb = "custom.nls.strings",
        name = "Zip Zap",
        desc = "Zip Zap Buy/Sell",
        menu = "Custom",
        overlay = true,
        studyOverlay = true,
        supportsBarUpdates = false,
        helpLink = "https://google.com")
public class ZipZap extends Study {
    enum Values {DELTA}

    final static String HIGH_INPUT = "highInput", LOW_INPUT = "lowInput", REVERSAL = "reversal", REVERSAL_TICKS = "reversalTicks", USE_TICKS = "useTicks", QTY = "quantity";
    final static String PRICE_MOVEMENTS = "priceMovements", PRICE_LABELS = "priceLabels", RETRACE_LINE = "retraceLine", OFFSET = "offset";

    @Override
    public void initialize(Defaults defaults) {
        var sd = createSD();
        var tab = sd.addTab(get("TAB_GENERAL"));
        boolean o = isOverlay();

        var grp = tab.addGroup(get("LBL_INPUTS"));
        grp.addRow(new DoubleDescriptor(QTY, "Buy/Sell Quantity", 1.0, 0.0001, 99.999, 0.0001));
        grp.addRow(new InputDescriptor(HIGH_INPUT, get("LBL_HIGH_INPUT"), Enums.BarInput.HIGH));
        grp.addRow(new InputDescriptor(LOW_INPUT, get("LBL_LOW_INPUT"), Enums.BarInput.LOW));
        grp.addRow(new IntegerDescriptor(REVERSAL_TICKS, get("LBL_REVERSAL_TICKS"), 10, 1, 99999, 1), new BooleanDescriptor(USE_TICKS, get("LBL_ENABLED"), false, false));
        grp.addRow(new DoubleDescriptor(REVERSAL, get("LBL_REVERSAL"), 1.0, 0.0001, 99.999, 0.0001));
        grp.addRow(new BooleanDescriptor(PRICE_MOVEMENTS, get("LBL_PRICE_MOVEMENTS"), true));
        grp.addRow(new BooleanDescriptor(PRICE_LABELS, get("LBL_PRICE_LABELS"), true));
        grp.addRow(new IntegerDescriptor(OFFSET, get("LBL_LABEL_OFFSET"), 5, 0, 99, 1));
        grp.addRow(new FontDescriptor(Inputs.FONT, get("LBL_FONT"), defaults.getFont()));

        grp = tab.addGroup(get("LBL_DISPLAY"));
        if (!o) {
            var histogram = new PathDescriptor(Inputs.BAR, get("LBL_HISTOGRAM"), defaults.getBarColor(), 1.0f, null, true, false, true);
            histogram.setShowAsBars(true);
            histogram.setSupportsShowAsBars(true);
            histogram.setColorPolicy(Enums.ColorPolicy.POSITIVE_NEGATIVE);
            histogram.setColor(defaults.getBarUpColor());
            histogram.setColor2(defaults.getBarDownColor());
            histogram.setColorPolicies(Enums.ColorPolicy.values());
            grp.addRow(histogram);
        }
        grp.addRow(new PathDescriptor(Inputs.PATH, get("LBL_LINE"), defaults.getLineColor(), 1.0f, null, true, false, true));
        grp.addRow(new PathDescriptor(RETRACE_LINE, get("LBL_RETRACE_LINE"), defaults.getLineColor(), 1.0f, new float[]{3f, 3f}, true, false, true));


        // Quick Settings (Tool Bar and Popup Editor)
        sd.addQuickSettings(HIGH_INPUT, LOW_INPUT, REVERSAL_TICKS, USE_TICKS, REVERSAL, PRICE_MOVEMENTS, PRICE_LABELS, Inputs.FONT, o ? null : Inputs.BAR, Inputs.PATH, RETRACE_LINE);
        sd.rowAlign(REVERSAL_TICKS, USE_TICKS);

        sd.addDependency(new EnabledDependency(USE_TICKS, REVERSAL_TICKS));
        sd.addDependency(new EnabledDependency(false, USE_TICKS, REVERSAL));
        sd.addDependency(new EnabledDependency(PRICE_LABELS, OFFSET));

        var rd = createRD();
        rd.setLabelSettings(HIGH_INPUT, LOW_INPUT, REVERSAL);
        rd.setIDSettings(HIGH_INPUT, LOW_INPUT, REVERSAL, REVERSAL_TICKS);
        rd.exportValue(new ValueDescriptor(Values.DELTA, Enums.ValueType.DOUBLE, get("LBL_DELTA"), null));
        if (!o) {
            rd.declarePath(Values.DELTA, Inputs.BAR);
            rd.setRangeKeys(Values.DELTA);
            rd.setTopInsetPixels(30);
            rd.setBottomInsetPixels(30);
            rd.addHorizontalLine(new LineInfo(0, null, 1.0f, new float[]{3f, 3f}));
        }
        setMinBars(200);
    }

    protected boolean isOverlay() {
        return true;
    }

    @Override
    public void clearState() {
        super.clearState();
        pivotBar = -1;
        pivot = 0;
    }

    private void buySell(PPoint pointOne, PPoint pointTwo) {
        if (pointOne.top) {
            Double average = (pointOne.coord.getValue() - pointTwo.coord.getValue()) / 2;
            // Should place a SELL order for average
        } else {
            Double average = (pointTwo.coord.getValue() - pointOne.coord.getValue()) / 2;
            // Should place a BUY order for average
        }
    }

    @Override
    protected void calculateValues(DataContext ctx) {
        var s = getSettings();
        Object highInput = s.getInput(HIGH_INPUT);
        Object lowInput = s.getInput(LOW_INPUT);
        double reversal = s.getDouble(REVERSAL, 1.0) / 100.0;
        int reversalTicks = s.getInteger(REVERSAL_TICKS, 10);
        boolean useTicks = s.getBoolean(USE_TICKS, false);
        int offset = s.getInteger(OFFSET, 5);
        double qty = s.getDouble(QTY, 0.0);
        var line = s.getPath(Inputs.PATH);
        var defaults = ctx.getDefaults();
        var fi = s.getFont(Inputs.FONT);
        Font f = fi == null ? defaults.getFont() : fi.getFont();
        Color bgColor = defaults.getBackgroundColor();
        Color txtColor = line.getColor();

        var series = ctx.getDataSeries();
        var instr = ctx.getInstrument();
        double tickAmount = reversalTicks * instr.getTickSize();

        if (pivotBar < 0) {
            // Initialize.
            double high = series.getDouble(0, highInput, 0);
            double low = series.getDouble(0, lowInput, 0);
            double val = series.getDouble(1, highInput, 0);
            up = val > high;
            pivotBar = 0;
            pivot = up ? low : high;
        }

        List<PPoint> points = new ArrayList<>();
        for (int i = pivotBar + 1; i < series.size(); i++) {
            if (!series.isBarComplete(i)) break;
            Double high = series.getDouble(i, highInput);
            Double low = series.getDouble(i, lowInput);
            if (high == null || low == null) continue;

            if (up) {
                if (useTicks ? high - pivot >= tickAmount : (1.0 - reversal) * high >= pivot) {
                    // confirmed previous low
                    points.add(new PPoint(new Coordinate(series.getStartTime(pivotBar), series.getLow(pivotBar)), pivotBar, false));
                    pivot = high;
                    pivotBar = i;
                    up = false;
                } else if (low < pivot) {
                    pivot = low;
                    pivotBar = i;
                }
            } else {
                if (useTicks ? pivot - low >= tickAmount : (1.0 + reversal) * low <= pivot) {
                    // confirmed previous max
                    points.add(new PPoint(new Coordinate(series.getStartTime(pivotBar), series.getHigh(pivotBar)), pivotBar, true));
                    pivot = low;
                    pivotBar = i;
                    up = true;
                } else if (high > pivot) {
                    pivot = high;
                    pivotBar = i;
                }
            }
        }

        int start = Math.max(0, points.size() - 3);
        int end = points.size() - 1;
        List<PPoint> newPoints = points.subList(start, end);

        // Build the ZigZag lines
        // For efficiency reasons, only build the delta
        beginFigureUpdate();

        int index = 1;
        for (var p : newPoints) {
            // Zig Zag Lines
            var c = p.coord;
            var lbl = new Label("Point Selected - " + index + " (value = " + c.getValue() + ")", f, txtColor, bgColor);
            lbl.setLocation(c.getTime(), isOverlay() ? c.getValue() : p.delta);
            lbl.setPosition(p.top ? Enums.Position.TOP : Enums.Position.BOTTOM);
            lbl.setShowLine(true);
            lbl.setOffset(offset);
            addFigure(lbl);
            index += 1;
        }

        endFigureUpdate();
    }

    private double pivot = 0;
    private int pivotBar = -1;
    private boolean up;

    private class PPoint {
        PPoint(Coordinate c, int ind, boolean top) {
            coord = c;
            this.top = top;
            this.ind = ind;
        }

        Coordinate coord;
        boolean top;
        int ind;
        float delta;
    }
}
