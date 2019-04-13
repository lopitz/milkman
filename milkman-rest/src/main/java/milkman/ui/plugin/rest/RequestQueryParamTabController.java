package milkman.ui.plugin.rest;

import static milkman.utils.FunctionalUtils.run;

import javafx.scene.control.Tab;
import lombok.SneakyThrows;
import milkman.domain.RequestContainer;
import milkman.ui.components.JfxTableEditor;
import milkman.ui.plugin.RequestAspectEditor;
import milkman.ui.plugin.rest.domain.QueryParamEntry;
import milkman.ui.plugin.rest.domain.RestQueryParamAspect;
import milkman.ui.plugin.rest.domain.RestRequestContainer;
import milkman.utils.fxml.FxmlUtil;

public class RequestQueryParamTabController implements RequestAspectEditor {


	@Override
	@SneakyThrows
	public Tab getRoot(RequestContainer request) {
		RestQueryParamAspect qryParams = request.getAspect(RestQueryParamAspect.class).get();
		JfxTableEditor<QueryParamEntry> editor = FxmlUtil.loadAndInitialize("/components/TableEditor.fxml");
		editor.setEditable(true);
		editor.addColumn("Name", QueryParamEntry::getName, run(QueryParamEntry::setName).andThen(() -> updateDirtyState(qryParams, request)));
		editor.addColumn("Value", QueryParamEntry::getValue,run(QueryParamEntry::setValue).andThen(() -> updateDirtyState(qryParams, request)));
		editor.addDeleteColumn("Delete", () -> updateDirtyState(qryParams, request));
		
		//TODO: the listener is fired before the item gets added
		//also the api should be like editor.enableEdit(creator, listener)
		// and editor.disableEdit();
		editor.setItems(qryParams.getEntries(), () -> {
			updateDirtyState(qryParams, request);
			return new QueryParamEntry("", "");
		});
		
		qryParams.onInvalidate.clear();
		qryParams.onInvalidate.add(() -> editor.setItems(qryParams.getEntries(), () -> {
			updateDirtyState(qryParams, request);
			return new QueryParamEntry("", "");
		}));
		
		return new Tab("Parameter", editor);
	}

	private void updateDirtyState(RestQueryParamAspect qryParams, RequestContainer request) {
		qryParams.setDirty(true);
		if (request instanceof RestRequestContainer) {
			RestRequestContainer restRequestContainer = (RestRequestContainer) request;
			String newUrl = qryParams.generateNewUrl(restRequestContainer.getUrl());
			restRequestContainer.setUrl(newUrl);
			request.onInvalidate.invoke(); //this issues a refresh of the url field
		}
	}

	@Override
	public boolean canHandleAspect(RequestContainer request) {
		return request.getAspect(RestQueryParamAspect.class).isPresent();
	}

}