package com.portofino.polygontrainmod.rail.math;

public interface ILine {
    double[] getPoint(int split, int index);

    int getNearlestPoint(int split, double z, double x);

    double getSlope(int split, int index);

    double getLength();
}
