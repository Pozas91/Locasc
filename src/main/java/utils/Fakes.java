package utils;

import models.applications.Application;
import models.applications.Provider;
import models.applications.Service;

import java.util.Collections;
import java.util.Map;

public class Fakes {
    /**
     * Given a provider index return a fake service to use that provider
     *
     * @param providerIndex Provider's index
     * @return A fake service
     */
    public static Service newService(Integer providerIndex) {
        return new Service("F_S", Collections.singletonList(providerIndex));
    }

    /**
     * Given an application and a composition, calculate a provider to can replace correctly
     *
     * @param app         An application to extract values
     * @param composition A composition which keys are services and values are providers selected.
     * @return A fake provider
     */
    public static Provider newProvider(Application app, Map<Integer, Integer> composition, Long threadId) {
        return app.copy(composition).convertToProvider(threadId);
    }
}
