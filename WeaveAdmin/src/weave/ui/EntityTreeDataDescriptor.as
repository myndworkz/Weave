package weave.ui
{
    import mx.collections.ICollectionView;
    import mx.controls.treeClasses.ITreeDataDescriptor;
    
    import weave.services.beans.Entity;
    
    public class EntityTreeDataDescriptor implements ITreeDataDescriptor
    {
        public function EntityTreeDataDescriptor()
        {
        }
		
		/*********************************
		 * ITreeDataDescriptor interface *
		 *********************************/
		
        public function addChildAt(parent:Object, newChild:Object, index:int, model:Object = null):Boolean
        {
			EntityNode.addChildAt(parent as EntityNode, newChild as EntityNode, index);
			return true;
        }
        public function removeChildAt(parent:Object, child:Object, index:int, model:Object = null):Boolean
        {
			if (child)
				EntityNode.removeChild(parent as EntityNode, child as EntityNode);
			return true;
        }
        public function getChildren(node:Object, model:Object = null):ICollectionView
        {
			return (node as EntityNode).children;
        }
        public function hasChildren(node:Object, model:Object = null):Boolean
        {
			return true;
			var children:ICollectionView = (node as EntityNode).children;
			return children && children.length;
        }
        public function getData(node:Object, model:Object = null):Object
        {
			return node as EntityNode;
        }
        public function isBranch(node:Object, model:Object = null):Boolean
        {
			var entity:Entity = (node as EntityNode).getEntity();
			return entity.type != Entity.TYPE_COLUMN && entity.type != Entity.TYPE_ANY;
        }
    }
}
