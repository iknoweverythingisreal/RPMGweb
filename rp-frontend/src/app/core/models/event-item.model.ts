export interface EventItem {
  eventItemId: number;          // ALWAYS mapping from backend: eventItem.id
  itemId: number;               // mapping from backend: itemId
  itemName: string;
  requestedQuantity: number;
  allocatedQuantity: number;
  status: string;
  uom: string;
}
