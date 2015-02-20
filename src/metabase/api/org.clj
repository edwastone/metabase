(ns metabase.api.org
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE]]
            [medley.core :refer :all]
            [metabase.api.common :refer :all]
            [metabase.db :refer :all]
            [metabase.models.hydrate :refer :all]
            (metabase.models [org :refer [Org]]
                             [user :refer [User]]
                             [org-perm :refer [OrgPerm]])
            [metabase.util :as util]))


(defendpoint GET "/" []
  ;; TODO - permissions check
  (sel :many Org))


(defendpoint POST "/" [:as {body :body}]
  ;; TODO - implementation
  {:status 200
   :body {}})


(defendpoint GET "/:id" [id]
  (let-404 [{:keys [can_read] :as org} (sel :one Org :id id)]
    (check-403 @can_read)
    org))


(defendpoint GET "/slug/:slug" [slug]
  (let-404 [{:keys [can_read] :as org} (sel :one Org :slug slug)]
    (check-403 @can_read)
    org))


(defendpoint PUT "/:id" [id :as {body :body}]
  (let-404 [{:keys [can_write] :as org} (sel :one Org :id id)]
    (check-403 @can_write)
    (check-500 (->> (util/select-non-nil-keys body :name :description :logo_url)
                 (mapply upd Org id)))
    (sel :one Org :id id)))


(defn grant-org-perm
  "Grants permission for given User on Org.  Creates record if needed, otherwise updates existing record."
  [org-id user-id is-admin]
  (let [perm (sel :one OrgPerm :user_id user-id :organization_id org-id)
        is-admin (boolean is-admin)]
    (if-not perm
      (ins OrgPerm
        :user_id user-id
        :organization_id org-id
        :admin is-admin)
      (upd OrgPerm (:id perm)
        :admin is-admin))))


(defendpoint GET "/:id/members" [id]
  (let-404 [{:keys [can_read] :as org} (sel :one Org :id id)]
    (check-403 @can_read)
    (-> (sel :many OrgPerm :organization_id id)
        (hydrate :user :organization))))


(defendpoint POST "/:id/members" [id :as {{:keys [first_name last_name email admin]} :body}]
  ; we require 4 attributes in the body
  (check-400 (and first_name last_name email admin))
  ; user must have admin perms on org to proceed
  (let-404 [{:keys [can_write] :as org} (sel :one Org :id id)]
    (check-403 @can_write)
    (let [user-id (:id (or (sel :one [User :id] :email email)                        ; find user with existing email - if exists then grant perm
                           (ins User
                             :email email
                             :first_name first_name
                             :last_name last_name
                             :password (str (java.util.UUID/randomUUID)))))]         ; TODO - send welcome email
      (grant-org-perm id user-id admin)
      (-> (sel :one OrgPerm :user_id user-id :organization_id id)
          (hydrate :user :organization)))))


(defendpoint POST "/:id/members/:user-id" [id user-id :as {body :body}]
  ; user must have admin perms on org to proceed
  (let-404 [{:keys [can_write] :as org} (sel :one Org :id id)]
    (check-403 @can_write)
    (let-404 [user (sel :one User :id user-id)]
      (grant-org-perm id user-id (or (:admin body) false))
      {:success true})))


(defendpoint PUT "/:id/members/:user-id" [id user-id :as {body :body}]
  ; user must have admin perms on org to proceed
  (let-404 [{:keys [can_write] :as org} (sel :one Org :id id)]
    (check-403 @can_write)
    (let-404 [user (sel :one User :id user-id)]
      (grant-org-perm id user-id (or (:admin body) false))
      {:success true})))


(defendpoint DELETE "/:id/members/:user-id" [id user-id :as {body :body}]
  ; user must have admin perms on org to proceed
  (let-404 [{:keys [can_write] :as org} (sel :one Org :id id)]
    (check-403 @can_write)
    (let-404 [user (sel :one User :id user-id)]
      (del OrgPerm :user_id user-id :organization_id id)
      {:success true})))


(define-routes)