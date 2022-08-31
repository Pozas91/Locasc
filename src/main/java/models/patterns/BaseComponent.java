package models.patterns;

import models.applications.Application;
import models.applications.Service;

import java.util.Collections;
import java.util.List;

public abstract class BaseComponent extends Component {
    protected Integer _id;

    public BaseComponent() {
        super();
    }

    public BaseComponent(BaseComponent o) {
        super(o);
        _id = o._id;
    }

    public Service getService(Application app) {
        return app.getService(_id);
    }

    public Integer getIService() {
        return _id;
    }

    @Override
    public List<Service> getServices(Application app) {
        return Collections.singletonList(getService(app));
    }

    @Override
    public List<BaseComponent> getBaseComponents() {
        return Collections.singletonList(this);
    }

    @Override
    public Architecture getArchitecture() {
        return new Sequential(Collections.singletonList(this));
    }

    @Override
    public Boolean isBase() {
        return true;
    }

    @Override
    public String toString() {
        return "{" + _id + '}';
    }
}
