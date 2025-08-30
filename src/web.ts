import { WebPlugin } from '@capacitor/core';

import type {
  AlbumsPathResponse,
  MediaAlbumCreate,
  MediaAlbumResponse,
  MediaFetchOptions,
  MediaPath,
  MediaPlugin,
  MediaResponse,
  MediaSaveOptions,
  PhotoResponse,
  PermissionStatus,
  PhotosResponse,
  PhotoInfo,
} from './definitions';

export class MediaWeb extends WebPlugin implements MediaPlugin {
  getMedias(options?: MediaFetchOptions): Promise<MediaResponse> {
    console.log('getMedias', options);
    throw this.unimplemented('Not implemented on web.');
  }
  getMediaByIdentifier(options: any): Promise<MediaPath> {
    console.log('getMediaByIdentifier', options);
    throw this.unimplemented('Not implemented on web.');
  }
  getAlbums(): Promise<MediaAlbumResponse> {
    throw this.unimplemented('Not implemented on web.');
  }
  savePhoto(options?: MediaSaveOptions): Promise<PhotoResponse> {
    console.log('savePhoto', options);
    throw this.unimplemented('Not implemented on web.');
  }
  saveVideo(options?: MediaSaveOptions): Promise<PhotoResponse> {
    console.log('saveVideo', options);
    throw this.unimplemented('Not implemented on web.');
  }
  createAlbum(options: MediaAlbumCreate): Promise<void> {
    console.log('createAlbum', options);
    throw this.unimplemented('Not implemented on web.');
  }
  getAlbumsPath(): Promise<AlbumsPathResponse> {
    console.log('getAlbumsPath');
    throw this.unimplemented('Not implemented on web.');
  }
  checkPermissions(): Promise<PermissionStatus> {
    console.log('checkPermissions');
    throw this.unimplemented('Not implemented on web.');
  }
  requestPermissions(): Promise<PermissionStatus> {
    console.log('requestPermissions');
    throw this.unimplemented('Not implemented on web.');
  }
  getPhotos(options?: { albumId?: string }): Promise<PhotosResponse> {
    console.log('getPhotos', options);
    throw this.unimplemented('Not implemented on web.');
  }
  getAllPhotos(): Promise<PhotosResponse> {
    console.log('getAllPhotos');
    throw this.unimplemented('Not implemented on web.');
  }
  getPhotoInfo(options: { path: string }): Promise<PhotoInfo> {
    console.log('getPhotoInfo', options);
    throw this.unimplemented('Not implemented on web.');
  }

  getFile(options: { contentUri: string; quality?: number; maxWidth?: number; maxHeight?: number }): Promise<{ base64Data: string; name: string; type: string; size: number }> {
    console.log('getFile', options);
    throw this.unimplemented('Not implemented on web.');
  }

  exitApp(): Promise<void> {
    console.log('exitApp');
    throw this.unimplemented('Not implemented on web.');
  }
}
