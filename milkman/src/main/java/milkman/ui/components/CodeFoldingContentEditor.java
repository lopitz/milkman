package milkman.ui.components;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Stack;
import java.util.function.IntFunction;

import com.jfoenix.controls.JFXButton;
import javafx.scene.control.Button;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.fxmisc.richtext.LineNumberFactory;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import lombok.Data;

/**
 * this editor supports code folding
 *
 * this should only be used for non-editable code Areas as it modifies the text and this might
 * result in unwanted changes to bound values
 */
public class CodeFoldingContentEditor extends ContentEditor {
	private ContentRange rootRange;

	private Button collapseAll;
	private Button expandAll;

	private Button collapseOne;
	private Button expandOne;

	private int currentFoldingLevel;
	private int maxFoldingLevel;

	private int minFoldingLevel = 1;

	public CodeFoldingContentEditor() {

		collapseAll = new JFXButton();
		collapseAll.setGraphic(new FontAwesomeIconView(FontAwesomeIcon.COMPRESS));
		collapseAll.setOnAction(e -> {
			setCollapseRecursively(rootRange, minFoldingLevel, 0);
			currentFoldingLevel = 0;
			redrawText();
		});


		expandAll = new JFXButton();
		expandAll.setGraphic(new FontAwesomeIconView(FontAwesomeIcon.EXPAND));
		expandAll.setOnAction(e -> {
			setCollapseRecursively(rootRange, maxFoldingLevel, 0);
			currentFoldingLevel = maxFoldingLevel;
			redrawText();
		});

		collapseOne = new JFXButton();
		collapseOne.setGraphic(new FontAwesomeIconView(FontAwesomeIcon.PLUS));
		collapseOne.setOnAction(e -> {
			int nextLevel = Math.min(currentFoldingLevel +1, maxFoldingLevel);
			setCollapseRecursively(rootRange, nextLevel, 0);
			currentFoldingLevel = nextLevel;
			redrawText();
		});


		expandOne = new JFXButton();
		expandOne.setGraphic(new FontAwesomeIconView(FontAwesomeIcon.MINUS));
		expandOne.setOnAction(e -> {
			int nextLevel = Math.max(currentFoldingLevel -1, minFoldingLevel);
			setCollapseRecursively(rootRange, nextLevel, 0);
			currentFoldingLevel = nextLevel;
			redrawText();
		});

		header.getChildren().add(collapseAll);
		header.getChildren().add(expandAll);
		header.getChildren().add(collapseOne);
		header.getChildren().add(expandOne);

		highlighters.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
			if (n != null){
				collapseAll.setVisible(n.supportFolding());
				expandAll.setVisible(n.supportFolding());
				collapseOne.setVisible(n.supportFolding());
				expandOne.setVisible(n.supportFolding());
				//trigger redraw bc of folding
				replaceText(codeArea.getText());
			}
		});

	}

	private void setCollapseRecursively(ContentRange node, int collapseAllBelowLevel, int curLevel) {
		if (node == null)
			return;

		if (node instanceof CollapsableRange){
			CollapsableRange collapsable = (CollapsableRange) node;
			if (!collapsable.isRoot())
				collapsable.setCollapsed(curLevel > collapseAllBelowLevel);

			for (ContentRange child : collapsable.getChildren()) {
				setCollapseRecursively(child, collapseAllBelowLevel, curLevel + 1);
			}
		}
	}

	@Override
	protected void setupParagraphGraphics() {
    	 IntFunction<Node> numberFactory = LineNumberFactory.get(codeArea);
         IntFunction<Node> arrowFactory = new FoldOperatorFactory();
         IntFunction<Node> graphicFactory = line -> {
             var collapseSign = arrowFactory.apply(line);
             
 			HBox hbox = new HBox(numberFactory.apply(line));
 			hbox.setPrefWidth(50);
 			if (collapseSign != null)
 				hbox.getChildren().add(collapseSign);
 			
             hbox.setAlignment(Pos.CENTER_LEFT);
             return hbox;
         };
         codeArea.setParagraphGraphicFactory(graphicFactory);
	}

    
    protected void replaceText(String text) {
    	if (getCurrentContenttypePlugin() != null && getCurrentContenttypePlugin().supportFolding())
    	{
    		rootRange = getCurrentContenttypePlugin().computeFolding(text);
			maxFoldingLevel = computeMaxFoldingLevel(rootRange);
			currentFoldingLevel = maxFoldingLevel; //start with expanded tree
        	redrawText();
    	} else {
    		super.replaceText(text);
    	}
    }

	private int computeMaxFoldingLevel(ContentRange node) {
		if (node instanceof CollapsableRange){
			CollapsableRange collapsable = (CollapsableRange) node;


			int childLvl = collapsable.getChildren()
					.stream()
					.mapToInt(this::computeMaxFoldingLevel)
					.max().orElse(0);

			return childLvl + (collapsable.isRoot() ? 0 : 1); //dont count in root
		}

		return 0;
	}


	private Optional<CollapsableRange> lookupCollapsableRangeInStartLineIdx(int lineNumber, ContentRange curContentRange){
    	if(!(curContentRange instanceof CollapsableRange)) {
    		return Optional.empty();
    	}
    	var collapsable = (CollapsableRange) curContentRange;
    	if (collapsable.getStartLine() == lineNumber)
    		return Optional.of(collapsable);
    	
    	if (!collapsable.isCollapsed()) {
    		for (ContentRange childRange : collapsable.getChildren()) {
    			var childMatch = lookupCollapsableRangeInStartLineIdx(lineNumber, childRange);
    			if (childMatch.isPresent())
    				return childMatch;
    		}
    	}
    	return Optional.empty();
    }

    private void redrawText() {
    	StringBuilder b = new StringBuilder();
    	rootRange.appendToString(b);
    	codeArea.replaceText(b.toString());
	}
    
	private class FoldOperatorFactory implements IntFunction<Node> {

        @Override
        public Node apply(int lineNumber) {
        	return lookupCollapsableRangeInStartLineIdx(lineNumber, rootRange)
        		.map(r -> {
        			FontAwesomeIconView view; 
					if (r.isCollapsed()) {
						view = new FontAwesomeIconView(FontAwesomeIcon.PLUS_SQUARE);
					} else {
						view = new FontAwesomeIconView(FontAwesomeIcon.MINUS_SQUARE);
					}
					view.setOnMouseClicked(e -> {
						r.setCollapsed(!r.isCollapsed());
						redrawText();
					});
					view.setStyleClass("handCursor");
					return view;
				}).orElse(null);
        }
    }
	
	@Data
	public abstract static class ContentRange {
		protected final ContentRange prevRange;

		public int getStartLine() {
			return prevRange == null ? 0 : prevRange.getEndLine();
		}

		public int getEndLine() {
			return getStartLine() + getContainedLines();
		}

		public abstract int getContainedLines();

		public abstract void appendToString(StringBuilder b);

	}

	@Data
	public static class TextRange extends ContentRange {
		private final String text;
		private final int containedLines;

		public TextRange(ContentRange prevRange, String text) {
			super(prevRange);
			this.text = text;
			containedLines = StringUtils.countMatches(text, '\n');
		}

		public void appendToString(StringBuilder b) {
			b.append(text);
		}
	}

	@Data
	public static class CollapsableRange extends ContentRange {
		private final boolean isRoot;
		private final int collapsedLines;
		private String collapsedText;
		private boolean collapsed;

		private List<ContentRange> children = new LinkedList<>();

		public CollapsableRange(ContentRange prevRange, boolean isRoot, String collapsedText) {
			super(prevRange);
			this.isRoot = isRoot;
			this.collapsedText = collapsedText;
			this.collapsedLines = StringUtils.countMatches(collapsedText, '\n');
		}

		public void setCollapsed(boolean value) {
			collapsed = value;
		}

		public void addChildren(ContentRange range) {
			children.add(range);
		}

		public void appendToString(StringBuilder b) {
			if (collapsed) {
				b.append(collapsedText);
			} else {
				for (ContentRange range : children) {
					range.appendToString(b);
				}
			}
		}

		public int getContainedLines() {
			if (collapsed)
				return collapsedLines;
			int sum = 0;
			for (ContentRange range : children) {
				sum += range.getContainedLines();
			}
			return sum;
		}

		public int getStartLine() {
			return isRoot ? -1 : super.getStartLine();
		}

	}

	public static class CodeFoldingBuilder {
    	private final String text;
    	private final Stack<CollapsableRange> rangeStack;
    	private int curIdx = 0;

		public CodeFoldingBuilder(String text) {
			this.text = text;
			rangeStack = new Stack<>();
			rangeStack.add(new CollapsableRange(null, true, ""));
		}

		/**
		 * will add everything from current idx to given idx as text node and add a new collapsable to the stack
		 * @param nextIdx
		 */
		public void startRange(int nextIdx, String placeholder){
			ContentRange prev = addLeftOverTextToCurrentRange(nextIdx);

			CollapsableRange newCollapsable = new CollapsableRange(prev, false, placeholder);
			rangeStack.peek().addChildren(newCollapsable);
			rangeStack.add(newCollapsable);
		}

		private ContentRange addLeftOverTextToCurrentRange(int nextIdx) {
			ContentRange prev = null;
			if (rangeStack.peek().getChildren().size() > 0)
				prev = rangeStack.peek().getChildren().get( rangeStack.peek().getChildren().size() -1);

			if (curIdx < nextIdx) {
				prev = new TextRange(prev, text.substring(curIdx, nextIdx));
				rangeStack.peek().addChildren(prev);
			}
			curIdx = nextIdx;
			return prev;
		}

		/**
		 * will close the current collapsable and pop it from stack
		 * @param nextIdx
		 */
		public void endRange(int nextIdx){
			ContentRange prev = addLeftOverTextToCurrentRange(nextIdx);

			//just as a guard, dont pop the root
			if (rangeStack.size() > 1)
				rangeStack.pop();
		}

		public CollapsableRange build(){
			ContentRange prev = addLeftOverTextToCurrentRange(text.length());
			return rangeStack.get(0);
		}

	}
}
