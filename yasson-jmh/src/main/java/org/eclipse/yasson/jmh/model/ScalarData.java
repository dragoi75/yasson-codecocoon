package org.eclipse.yasson.jmh.model;

public class ScalarData {

    private String stringValue;

    private Integer integerValue;

    public Integer getIntegerValue() {
        return integerValue;
    }

    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }

    public void setIntegerValue(Integer integerValue) {
        this.integerValue = integerValue;
    }

    public String getStringValue() {
        return stringValue;
    }

    public ScalarData() {
        this.stringValue = "Short string";
        this.integerValue = 10;
    }

    public ScalarData(String stringValue, Integer integerValue) {
        this.stringValue = stringValue;
        this.integerValue = integerValue;
    }

}
