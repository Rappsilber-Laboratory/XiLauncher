/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package rappsilber.xilauncher;

/**
 *
 * @author lfischer
 */
class ObjectWrapper<T> {
    T v = null;
    public T setValue(T v) {
        T o = v;
        this.v= v;
        return o;
    }
    public T getValue() {
        return this.v;
    }

    public String toString() {
        return v.toString();
    }
}
