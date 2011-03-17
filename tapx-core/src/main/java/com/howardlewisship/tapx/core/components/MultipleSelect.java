package com.howardlewisship.tapx.core.components;

import java.util.Set;

import org.apache.tapestry5.BindingConstants;
import org.apache.tapestry5.ComponentAction;
import org.apache.tapestry5.ComponentResources;
import org.apache.tapestry5.annotations.Environmental;
import org.apache.tapestry5.annotations.Import;
import org.apache.tapestry5.annotations.Parameter;
import org.apache.tapestry5.annotations.Property;
import org.apache.tapestry5.corelib.components.Palette;
import org.apache.tapestry5.ioc.annotations.Inject;
import org.apache.tapestry5.json.JSONArray;
import org.apache.tapestry5.json.JSONObject;
import org.apache.tapestry5.services.FormSupport;
import org.apache.tapestry5.services.javascript.JavaScriptSupport;

import com.howardlewisship.tapx.core.multiselect.MultipleSelectModel;

/**
 * Both a simplification of the Tapestry {@link Palette} component, and an extension in that it supports
 * adding new values on the fly on the client side.
 */
@Import(stack = "tapx-core")
public class MultipleSelect
{
    /**
     * The set of values edited by the component; this is used when rendering. When the form is submitted,
     * the values are first cleared, then repopulated with the selected values (some of which may be created
     * for the first time as part of this process).
     */
    @Parameter(required = true, allowNull = false, autoconnect = true)
    private Set<?> values;

    @SuppressWarnings("rawtypes")
    @Parameter(required = true, allowNull = false)
    private MultipleSelectModel model;

    /**
     * Additional CSS class(es), beyond the mandatory default of "tx-multiselect".
     */
    @Parameter(name = "class", defaultPrefix = BindingConstants.LITERAL)
    private String className;

    @Environmental
    private FormSupport formSupport;

    @Environmental
    private JavaScriptSupport jss;

    @Inject
    private ComponentResources resources;

    @Property(write = false)
    private String clientId, name;

    public static class ProcessSubmission implements ComponentAction<MultipleSelect>
    {
        private static final long serialVersionUID = -5387860921749570953L;

        private final String name;

        public ProcessSubmission(String name)
        {
            this.name = name;
        }

        @Override
        public void execute(MultipleSelect component)
        {
            component.processSubmission(name);
        }

    }

    void setupRender()
    {
        clientId = jss.allocateClientId(resources);

        name = formSupport.allocateControlName(resources.getId());

        writeJavaScript(clientId, name);
    }

    public String getComputedClassName()
    {
        return className == null ? "tx-multiselect" : "tx-multiselect " + className;
    }

    @SuppressWarnings("unchecked")
    private void writeJavaScript(String clientId, String name)
    {
        JSONObject spec = new JSONObject("clientId", clientId);

        for (Object value : values)
        {
            String clientValue = model.toClient(value);

            spec.append("values", clientValue);
        }

        for (Object value : model.getAvailableValues())
        {
            String clientValue = model.toClient(value);
            String label = model.toLabel(value);

            JSONArray row = new JSONArray(clientValue, label);
            spec.append("model", row);
        }

        jss.addInitializerCall("tapxMultipleSelect", spec);
    }

    private void processSubmission(String name)
    {

    }
}
